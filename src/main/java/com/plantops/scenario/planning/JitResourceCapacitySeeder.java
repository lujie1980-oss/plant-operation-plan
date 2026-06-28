package com.plantops.scenario.planning;

import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.config.ParameterRegistry;
import com.plantops.scenario.WorkOrderTimingService;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSlotTimes;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JIT 倒排预排：在 OR-Tools 求解前为 {@link ResourceCapacityAssignment} 播种 (timeSlot, assignedMinutes)，
 * 并作为 CP-SAT hint / 可行快路径输入。
 */
@ApplicationScoped
public class JitResourceCapacitySeeder {

    @Inject
    ParameterRegistry parameters;

    @Inject
    WorkOrderTimingService workOrderTimingService;

    public boolean isEnabled() {
        return parameters.getBoolean("master_plan_jit_warm_start", true);
    }

    public void seedIfEnabled(MasterPlanSchedule schedule) {
        if (!isEnabled() || schedule == null || !schedule.hasResourceCapacityAssignments()) {
            return;
        }
        seed(schedule);
    }

    public void seed(MasterPlanSchedule schedule) {
        List<ResourceCapacityAssignment> assignments = schedule.getResourceCapacityAssignments();
        if (assignments == null || assignments.isEmpty()) {
            return;
        }

        Map<String, WorkOrderTimingWindowDto> timingByWorkOrder = loadTimingWindows(assignments);
        Map<String, Integer> slotLoadMinutes = initialSlotLoads(schedule.getCapacityOverlay());
        Map<String, List<ResourceCapacityAssignment>> byWorkOrder = groupByWorkOrder(assignments);

        for (String workOrderNo : parentFirstBomOrder(schedule.getBomDependencyEdges(), byWorkOrder.keySet())) {
            List<ResourceCapacityAssignment> woAssignments = byWorkOrder.get(workOrderNo);
            if (woAssignments == null || woAssignments.isEmpty()) {
                continue;
            }
            LocalDateTime endAnchor = resolveEndAnchor(
                    workOrderNo,
                    schedule.getBomDependencyEdges(),
                    timingByWorkOrder,
                    assignments);
            seedWorkOrderBackward(woAssignments, endAnchor, slotLoadMinutes);
        }
    }

    private Map<String, WorkOrderTimingWindowDto> loadTimingWindows(List<ResourceCapacityAssignment> assignments) {
        Map<String, WorkOrderTimingWindowDto> timingByWorkOrder = new HashMap<>();
        Set<String> workOrders = new LinkedHashSet<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getWorkOrderNo() != null) {
                workOrders.add(assignment.getWorkOrderNo());
            }
        }
        for (String workOrderNo : workOrders) {
            WorkOrderTimingWindowDto window = workOrderTimingService.compute(workOrderNo, null);
            if (window != null) {
                timingByWorkOrder.put(workOrderNo, window);
            }
        }
        return timingByWorkOrder;
    }

    private static Map<String, Integer> initialSlotLoads(MasterPlanCapacityOverlay overlay) {
        Map<String, Integer> slotLoadMinutes = new HashMap<>();
        if (overlay == null) {
            return slotLoadMinutes;
        }
        overlay.fixedMinutesBySlotId().forEach(slotLoadMinutes::put);
        return slotLoadMinutes;
    }

    private static LocalDateTime resolveEndAnchor(
            String workOrderNo,
            List<BomDependencyEdge> bomEdges,
            Map<String, WorkOrderTimingWindowDto> timingByWorkOrder,
            List<ResourceCapacityAssignment> allAssignments) {
        if (bomEdges != null) {
            for (BomDependencyEdge edge : bomEdges) {
                if (!edge.childWorkOrderNo().equals(workOrderNo)) {
                    continue;
                }
                LocalDateTime parentStart = earliestPlannedStart(edge.parentWorkOrderNo(), allAssignments);
                if (parentStart != null) {
                    return parentStart;
                }
            }
        }
        WorkOrderTimingWindowDto window = timingByWorkOrder.get(workOrderNo);
        if (window != null && window.latestDesiredEnd() != null) {
            return window.latestDesiredEnd();
        }
        return LocalDateTime.now().plusDays(7).withHour(17).withMinute(0).withSecond(0).withNano(0);
    }

    private static LocalDateTime earliestPlannedStart(String workOrderNo, List<ResourceCapacityAssignment> assignments) {
        LocalDateTime earliest = null;
        for (ResourceCapacityAssignment assignment : assignments) {
            if (!workOrderNo.equals(assignment.getWorkOrderNo())
                    || assignment.getTimeSlot() == null
                    || assignment.getAssignedMinutes() <= 0) {
                continue;
            }
            LocalDateTime start = MasterPlanSlotTimes.slotStart(assignment.getTimeSlot());
            if (earliest == null || start.isBefore(earliest)) {
                earliest = start;
            }
        }
        return earliest;
    }

    static void seedWorkOrderBackward(
            List<ResourceCapacityAssignment> woAssignments,
            LocalDateTime endAnchor,
            Map<String, Integer> slotLoadMinutes) {
        Map<Integer, List<ResourceCapacityAssignment>> byOpSeq = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : woAssignments) {
            if (assignment.isLocked()) {
                continue;
            }
            assignment.setTimeSlot(null);
            assignment.setAssignedMinutes(0);
            byOpSeq.computeIfAbsent(assignment.getOperationSeq(), ignored -> new ArrayList<>()).add(assignment);
        }

        List<Integer> opSeqs = byOpSeq.keySet().stream().sorted(Comparator.reverseOrder()).toList();
        Map<Integer, LocalDateTime> opEndAnchors = operationEndAnchors(woAssignments);
        LocalDateTime cursorEnd = endAnchor;
        for (int opSeq : opSeqs) {
            List<ResourceCapacityAssignment> opGroup = byOpSeq.get(opSeq);
            LocalDateTime opAnchor = opEndAnchors.getOrDefault(opSeq, cursorEnd);
            LocalDateTime opStart = placeOperationBackward(opGroup, opAnchor, slotLoadMinutes);
            if (opStart != null) {
                cursorEnd = opStart;
            }
        }
    }

    private static Map<Integer, LocalDateTime> operationEndAnchors(List<ResourceCapacityAssignment> woAssignments) {
        Map<Integer, LocalDateTime> anchors = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : woAssignments) {
            if (assignment.getOperationLatestDesiredEnd() != null) {
                anchors.putIfAbsent(assignment.getOperationSeq(), assignment.getOperationLatestDesiredEnd());
            }
        }
        return anchors;
    }

    private static LocalDateTime placeOperationBackward(
            List<ResourceCapacityAssignment> opGroup,
            LocalDateTime endAnchor,
            Map<String, Integer> slotLoadMinutes) {
        if (opGroup == null || opGroup.isEmpty()) {
            return null;
        }
        int remaining = opGroup.get(0).getOperationTotalMinutes();
        Map<String, List<ResourceCapacityAssignment>> byResource = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : opGroup) {
            byResource.computeIfAbsent(assignment.getResourceId(), ignored -> new ArrayList<>()).add(assignment);
        }

        List<String> resources = byResource.keySet().stream()
                .sorted(Comparator.comparingInt(rid -> byResource.get(rid).get(0).getResourcePriority()))
                .toList();

        LocalDateTime earliestStart = null;
        for (String resourceId : resources) {
            if (remaining <= 0) {
                break;
            }
            List<ResourceCapacityAssignment> resourceAssignments = byResource.get(resourceId).stream()
                    .sorted(Comparator.comparingInt(ResourceCapacityAssignment::getDaySegmentIndex))
                    .toList();
            for (ResourceCapacityAssignment assignment : resourceAssignments) {
                if (remaining <= 0) {
                    break;
                }
                Placement placement = findBackwardPlacement(assignment, endAnchor, remaining, slotLoadMinutes);
                if (placement == null) {
                    continue;
                }
                assignment.setTimeSlot(placement.slot());
                assignment.setAssignedMinutes(placement.minutes());
                slotLoadMinutes.merge(placement.slot().getId(), placement.minutes(), Integer::sum);
                remaining -= placement.minutes();
                LocalDateTime start = MasterPlanSlotTimes.slotStart(placement.slot());
                if (earliestStart == null || start.isBefore(earliestStart)) {
                    earliestStart = start;
                }
            }
        }

        if (remaining > 0) {
            spillRemaining(opGroup, remaining, endAnchor, slotLoadMinutes);
        }
        assignZeroLoadSlots(opGroup);
        return earliestStart;
    }

    private static void spillRemaining(
            List<ResourceCapacityAssignment> opGroup,
            int remaining,
            LocalDateTime endAnchor,
            Map<String, Integer> slotLoadMinutes) {
        for (ResourceCapacityAssignment assignment : opGroup) {
            if (remaining <= 0 || assignment.getAssignedMinutes() > 0) {
                continue;
            }
            Placement placement = findBackwardPlacement(assignment, endAnchor, remaining, slotLoadMinutes);
            if (placement == null) {
                continue;
            }
            assignment.setTimeSlot(placement.slot());
            assignment.setAssignedMinutes(placement.minutes());
            slotLoadMinutes.merge(placement.slot().getId(), placement.minutes(), Integer::sum);
            remaining -= placement.minutes();
        }
    }

    private static void assignZeroLoadSlots(List<ResourceCapacityAssignment> opGroup) {
        for (ResourceCapacityAssignment assignment : opGroup) {
            if (assignment.getAssignedMinutes() > 0 || assignment.getEligibleTimeSlots().isEmpty()) {
                continue;
            }
            assignment.setTimeSlot(assignment.getEligibleTimeSlots().get(0));
            assignment.setAssignedMinutes(0);
        }
    }

    private record Placement(TimeSlot slot, int minutes) {
    }

    private static Placement findBackwardPlacement(
            ResourceCapacityAssignment assignment,
            LocalDateTime endAnchor,
            int remaining,
            Map<String, Integer> slotLoadMinutes) {
        List<TimeSlot> candidates = assignment.getEligibleTimeSlots().stream()
                .filter(slot -> assignment.getResourceId().equals(slot.getResourceId()))
                .sorted(Comparator.comparingInt(TimeSlot::getIndex).reversed())
                .toList();
        for (TimeSlot slot : candidates) {
            LocalDateTime slotStart = MasterPlanSlotTimes.slotStart(slot);
            if (endAnchor != null && slotStart.isAfter(endAnchor)) {
                continue;
            }
            int used = slotLoadMinutes.getOrDefault(slot.getId(), 0);
            int available = Math.max(0, slot.getCapacityMinutes() - used);
            if (available <= 0) {
                continue;
            }
            int chunk = Math.min(remaining, Math.min(available, assignment.getSlotCapacityMinutes()));
            if (chunk <= 0) {
                continue;
            }
            return new Placement(slot, chunk);
        }
        return null;
    }

    static List<String> parentFirstBomOrder(List<BomDependencyEdge> edges, Set<String> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
        Set<String> hasParent = new HashSet<>();
        if (edges != null) {
            for (BomDependencyEdge edge : edges) {
                if (!workOrders.contains(edge.parentWorkOrderNo())
                        || !workOrders.contains(edge.childWorkOrderNo())) {
                    continue;
                }
                childrenByParent.computeIfAbsent(edge.parentWorkOrderNo(), ignored -> new ArrayList<>())
                        .add(edge.childWorkOrderNo());
                hasParent.add(edge.childWorkOrderNo());
            }
        }
        List<String> roots = workOrders.stream()
                .filter(wo -> !hasParent.contains(wo))
                .sorted()
                .toList();
        List<String> order = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String root : roots) {
            collectBomDepthFirst(root, childrenByParent, seen, order);
        }
        for (String wo : workOrders.stream().sorted().toList()) {
            if (seen.add(wo)) {
                order.add(wo);
            }
        }
        return order;
    }

    private static void collectBomDepthFirst(
            String workOrderNo,
            Map<String, List<String>> childrenByParent,
            Set<String> seen,
            List<String> order) {
        if (!seen.add(workOrderNo)) {
            return;
        }
        order.add(workOrderNo);
        List<String> children = childrenByParent.get(workOrderNo);
        if (children == null) {
            return;
        }
        children.stream().sorted().forEach(child -> collectBomDepthFirst(child, childrenByParent, seen, order));
    }

    private static Map<String, List<ResourceCapacityAssignment>> groupByWorkOrder(
            List<ResourceCapacityAssignment> assignments) {
        Map<String, List<ResourceCapacityAssignment>> byWorkOrder = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getWorkOrderNo() == null) {
                continue;
            }
            byWorkOrder.computeIfAbsent(assignment.getWorkOrderNo(), ignored -> new ArrayList<>()).add(assignment);
        }
        return byWorkOrder;
    }
}
