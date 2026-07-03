package com.plantops.scenario.planning.optimizer.ortools;

import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCalendarMoments;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.MaterialFeasibilityEvaluator;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OR-Tools 多机台 CP 不可行时的启发式诊断（供异常信息展示）。 */
public final class ResourceCapacityCpDiagnostics {

    private ResourceCapacityCpDiagnostics() {
    }

    public static String summarize(MasterPlanSchedule schedule, List<ResourceCapacityAssignment> assignments) {
        List<String> issues = new ArrayList<>();
        collect(schedule, assignments, issues);
        if (issues.isEmpty()) {
            return "no obvious structural conflict detected";
        }
        return String.join("; ", issues.subList(0, Math.min(3, issues.size())));
    }

    public static String probeRelaxationHint(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            boolean materialEnabled) {
        List<String> hints = new ArrayList<>();
        if (materialEnabled) {
            hints.add("try master_plan_material_constraint_enabled=false");
        }
        long operations = assignments.stream()
                .map(ResourceCapacityAssignment::getOperationKey)
                .distinct()
                .count();
        if (assignments.size() > operations * 2L) {
            hints.add("check multi-machine day-segment expansion (" + assignments.size() + " RCA / "
                    + operations + " ops)");
        }
        int bomEdges = schedule.getBomDependencyEdges() != null ? schedule.getBomDependencyEdges().size() : 0;
        if (bomEdges > 0) {
            hints.add("review BOM day order across " + bomEdges + " edges (solver auto-relaxes BOM on failure)");
        }
        if (hints.isEmpty()) {
            return "probe: tighten horizon/capacity or disable JIT warm start";
        }
        return "probe: " + String.join(", ", hints);
    }

    public static void collect(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        if (assignments == null || assignments.isEmpty()) {
            issues.add("no ResourceCapacityAssignment entities");
            return;
        }
        checkMaterial(schedule, assignments, issues);
        checkOperationConservation(assignments, issues);
        checkEligibleSlots(schedule, assignments, issues);
        checkBomDayOrder(schedule.getBomDependencyEdges(), assignments, issues);
        checkBomCycle(schedule.getBomDependencyEdges(), issues);
    }

    private static void checkGlobalCapacity(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        Map<String, Integer> operationMinutes = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            operationMinutes.putIfAbsent(assignment.getOperationKey(), assignment.getOperationTotalMinutes());
        }
        int demandMinutes = operationMinutes.values().stream().mapToInt(Integer::intValue).sum();
        var overlay = schedule.getCapacityOverlay();
        int supplyMinutes = schedule.getTimeSlotRange().stream()
                .filter(slot -> slot != null && overlay.isSlotEligibleForReplan(slot))
                .mapToInt(slot -> Math.max(
                        0,
                        slot.getCapacityMinutes() - overlay.fixedMinutesForSlot(slot.getId())))
                .sum();
        if (demandMinutes > supplyMinutes) {
            issues.add("global demand " + demandMinutes + " min exceeds replannable capacity "
                    + supplyMinutes + " min");
        }
    }

    private static void checkPerResourceCapacity(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        Map<String, Integer> demandByResource = new LinkedHashMap<>();
        Map<String, Integer> operationMinutes = new LinkedHashMap<>();
        Map<String, String> operationPrimaryResource = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            operationMinutes.putIfAbsent(assignment.getOperationKey(), assignment.getOperationTotalMinutes());
            operationPrimaryResource.putIfAbsent(assignment.getOperationKey(), assignment.getResourceId());
        }
        for (Map.Entry<String, Integer> entry : operationMinutes.entrySet()) {
            String resourceId = operationPrimaryResource.get(entry.getKey());
            if (resourceId != null) {
                demandByResource.merge(resourceId, entry.getValue(), Integer::sum);
            }
        }
        var overlay = schedule.getCapacityOverlay();
        Map<String, Integer> supplyByResource = new LinkedHashMap<>();
        for (TimeSlot slot : schedule.getTimeSlotRange()) {
            if (slot == null || !overlay.isSlotEligibleForReplan(slot)) {
                continue;
            }
            int capacity = Math.max(
                    0,
                    slot.getCapacityMinutes() - overlay.fixedMinutesForSlot(slot.getId()));
            supplyByResource.merge(slot.getResourceId(), capacity, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : demandByResource.entrySet()) {
            int supply = supplyByResource.getOrDefault(entry.getKey(), 0);
            if (entry.getValue() > supply) {
                issues.add("resource " + entry.getKey() + " demand " + entry.getValue()
                        + " min exceeds capacity " + supply + " min");
            }
        }
    }

    private static void checkBomDayOrder(
            List<BomDependencyEdge> edges,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        Map<String, Integer> firstOpSeq = new HashMap<>();
        Map<String, Integer> lastOpSeq = new HashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            firstOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::min);
            lastOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::max);
        }
        Map<String, Integer> opEarliestDay = new LinkedHashMap<>();
        Map<String, Integer> opLatestDay = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            int minDay = assignment.getEligibleTimeSlots().stream()
                    .mapToInt(MasterPlanCalendarMoments::slotDayOrdinal)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            int maxDay = assignment.getEligibleTimeSlots().stream()
                    .mapToInt(MasterPlanCalendarMoments::slotDayOrdinal)
                    .max()
                    .orElse(Integer.MIN_VALUE);
            if (minDay == Integer.MAX_VALUE) {
                continue;
            }
            String opKey = assignment.getOperationKey();
            opEarliestDay.merge(opKey, minDay, Math::min);
            opLatestDay.merge(opKey, maxDay, Math::max);
        }
        for (BomDependencyEdge edge : edges) {
            Integer childLast = lastOpSeq.get(edge.childWorkOrderNo());
            Integer parentFirst = firstOpSeq.get(edge.parentWorkOrderNo());
            if (childLast == null || parentFirst == null) {
                continue;
            }
            String childKey = com.plantops.solver.masterplan.ResourceCapacityAssignment.operationKey(
                    edge.childWorkOrderNo(), childLast);
            String parentKey = com.plantops.solver.masterplan.ResourceCapacityAssignment.operationKey(
                    edge.parentWorkOrderNo(), parentFirst);
            Integer childMin = opEarliestDay.get(childKey);
            Integer parentMin = opEarliestDay.get(parentKey);
            Integer childMax = opLatestDay.get(childKey);
            Integer parentMax = opLatestDay.get(parentKey);
            if (childMin == null || parentMin == null || childMax == null || parentMax == null) {
                continue;
            }
            if (childMin > parentMax) {
                issues.add("BOM day window disjoint " + edge.childWorkOrderNo() + "->" + edge.parentWorkOrderNo());
            }
        }
    }

    private static void checkMaterial(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        MasterPlanSettings settings = schedule.getPlanningSettings();
        if (settings == null || !settings.isMaterialConstraintEnabled()) {
            return;
        }
        MaterialFeasibilityContext context = schedule.getMaterialFeasibility();
        if (context == null) {
            return;
        }
        Map<String, List<ResourceCapacityAssignment>> byOp = groupByOperation(assignments);
        for (Map.Entry<String, List<ResourceCapacityAssignment>> entry : byOp.entrySet()) {
            boolean anyFeasibleDate = false;
            for (ResourceCapacityAssignment assignment : entry.getValue()) {
                for (TimeSlot slot : assignment.getEligibleTimeSlots()) {
                    if (slot == null) {
                        continue;
                    }
                    if (MaterialFeasibilityEvaluator.isFeasible(
                            assignment.getProductCode(),
                            assignment.getWorkOrderQuantity(),
                            slot.getDate(),
                            context)) {
                        anyFeasibleDate = true;
                        break;
                    }
                }
                if (anyFeasibleDate) {
                    break;
                }
            }
            if (!anyFeasibleDate) {
                issues.add("material infeasible for operation " + entry.getKey());
            }
        }
    }

    private static void checkOperationConservation(
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        Map<String, Boolean> hasEligible = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            hasEligible.putIfAbsent(assignment.getOperationKey(), false);
            if (!assignment.getEligibleTimeSlots().isEmpty()) {
                hasEligible.put(assignment.getOperationKey(), true);
            }
        }
        for (Map.Entry<String, Boolean> entry : hasEligible.entrySet()) {
            if (!entry.getValue()) {
                issues.add("no eligible slot for operation " + entry.getKey());
            }
        }
    }

    private static void checkEligibleSlots(
            MasterPlanSchedule schedule,
            List<ResourceCapacityAssignment> assignments,
            List<String> issues) {
        var overlay = schedule.getCapacityOverlay();
        for (ResourceCapacityAssignment assignment : assignments) {
            long replannable = assignment.getEligibleTimeSlots().stream()
                    .filter(slot -> slot != null && overlay.isSlotEligibleForReplan(slot))
                    .count();
            if (replannable == 0) {
                issues.add("no replan slot for " + assignment.getId());
            }
        }
    }

    private static void checkBomCycle(List<BomDependencyEdge> edges, List<String> issues) {
        if (edges == null || edges.isEmpty()) {
            return;
        }
        Map<String, Set<String>> childrenByParent = new HashMap<>();
        for (BomDependencyEdge edge : edges) {
            childrenByParent.computeIfAbsent(edge.parentWorkOrderNo(), ignored -> new LinkedHashSet<>())
                    .add(edge.childWorkOrderNo());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : childrenByParent.keySet()) {
            if (hasCycle(node, childrenByParent, visiting, visited)) {
                issues.add("BOM cycle involving " + node);
                return;
            }
        }
    }

    private static boolean hasCycle(
            String node,
            Map<String, Set<String>> childrenByParent,
            Set<String> visiting,
            Set<String> visited) {
        if (!visited.add(node)) {
            return visiting.contains(node);
        }
        visiting.add(node);
        for (String child : childrenByParent.getOrDefault(node, Set.of())) {
            if (hasCycle(child, childrenByParent, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        return false;
    }

    private static Map<String, List<ResourceCapacityAssignment>> groupByOperation(
            List<ResourceCapacityAssignment> assignments) {
        Map<String, List<ResourceCapacityAssignment>> byOp = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            byOp.computeIfAbsent(assignment.getOperationKey(), ignored -> new ArrayList<>()).add(assignment);
        }
        return byOp;
    }
}
