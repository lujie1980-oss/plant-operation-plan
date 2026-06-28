package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCalendarMoments;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.MaterialFeasibilityEvaluator;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 校验 JIT 预排种子是否满足多机台 CP 硬约束（用于可行快路径）。 */
public final class ResourceCapacitySeedValidator {

    private ResourceCapacitySeedValidator() {
    }

    public static boolean isFeasible(List<ResourceCapacityAssignment> assignments, MasterPlanSchedule schedule) {
        if (assignments == null || assignments.isEmpty()) {
            return false;
        }
        if (!operationTotalsConserved(assignments)) {
            return false;
        }
        // 槽位产能为软约束：JIT 种子不因超负荷被拒绝。
        if (!materialFeasible(assignments, schedule)) {
            return false;
        }
        if (!operationPrecedenceRespected(assignments, schedule.getOperationPrecedenceFacts())) {
            return false;
        }
        if (!bomPrecedenceRespected(assignments, schedule.getBomDependencyEdges())) {
            return false;
        }
        return true;
    }

    private static boolean operationTotalsConserved(List<ResourceCapacityAssignment> assignments) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getTimeSlot() == null) {
                return false;
            }
            totals.merge(assignment.getOperationKey(), assignment.getAssignedMinutes(), Integer::sum);
            expected.putIfAbsent(assignment.getOperationKey(), assignment.getOperationTotalMinutes());
        }
        for (Map.Entry<String, Integer> entry : expected.entrySet()) {
            if (!entry.getValue().equals(totals.getOrDefault(entry.getKey(), -1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean materialFeasible(
            List<ResourceCapacityAssignment> assignments,
            MasterPlanSchedule schedule) {
        MasterPlanSettings settings = schedule.getPlanningSettings();
        if (settings == null || !settings.isMaterialConstraintEnabled()) {
            return true;
        }
        MaterialFeasibilityContext context = schedule.getMaterialFeasibility();
        if (context == null) {
            return true;
        }
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getAssignedMinutes() <= 0 || assignment.getTimeSlot() == null) {
                continue;
            }
            if (!MaterialFeasibilityEvaluator.isFeasible(
                    assignment.getProductCode(),
                    assignment.getWorkOrderQuantity(),
                    assignment.getTimeSlot().getDate(),
                    context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean operationPrecedenceRespected(
            List<ResourceCapacityAssignment> assignments,
            List<OperationPrecedenceFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return true;
        }
        Map<String, LocalDate> opLatestDay = operationLatestPlanningDays(assignments);
        Map<String, LocalDate> opEarliestDay = operationEarliestPlanningDays(assignments);
        for (OperationPrecedenceFact fact : facts) {
            String pred = ResourceCapacityAssignment.operationKey(
                    fact.workOrderNo(), fact.predecessorOperationSeq());
            String succ = ResourceCapacityAssignment.operationKey(
                    fact.workOrderNo(), fact.successorOperationSeq());
            LocalDate predDay = opLatestDay.get(pred);
            LocalDate succDay = opEarliestDay.get(succ);
            if (predDay != null && succDay != null
                    && MasterPlanCalendarMoments.violatesPlanningDayOrder(predDay, succDay)) {
                return false;
            }
        }
        return true;
    }

    private static boolean bomPrecedenceRespected(
            List<ResourceCapacityAssignment> assignments,
            List<BomDependencyEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return true;
        }
        Map<String, Integer> firstOpSeq = new HashMap<>();
        Map<String, Integer> lastOpSeq = new HashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getAssignedMinutes() <= 0) {
                continue;
            }
            firstOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::min);
            lastOpSeq.merge(assignment.getWorkOrderNo(), assignment.getOperationSeq(), Math::max);
        }
        Map<String, LocalDate> opLatestDay = operationLatestPlanningDays(assignments);
        Map<String, LocalDate> opEarliestDay = operationEarliestPlanningDays(assignments);
        for (BomDependencyEdge edge : edges) {
            Integer childLast = lastOpSeq.get(edge.childWorkOrderNo());
            Integer parentFirst = firstOpSeq.get(edge.parentWorkOrderNo());
            if (childLast == null || parentFirst == null) {
                continue;
            }
            String childKey = ResourceCapacityAssignment.operationKey(edge.childWorkOrderNo(), childLast);
            String parentKey = ResourceCapacityAssignment.operationKey(edge.parentWorkOrderNo(), parentFirst);
            LocalDate childDay = opLatestDay.get(childKey);
            LocalDate parentDay = opEarliestDay.get(parentKey);
            if (childDay != null && parentDay != null
                    && MasterPlanCalendarMoments.violatesPlanningDayOrder(childDay, parentDay)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, LocalDate> operationLatestPlanningDays(List<ResourceCapacityAssignment> assignments) {
        Map<String, LocalDate> result = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getAssignedMinutes() <= 0 || assignment.getTimeSlot() == null) {
                continue;
            }
            LocalDate day = assignment.getTimeSlot().getDate();
            result.merge(assignment.getOperationKey(), day, (left, right) -> left.isAfter(right) ? left : right);
        }
        return result;
    }

    private static Map<String, LocalDate> operationEarliestPlanningDays(List<ResourceCapacityAssignment> assignments) {
        Map<String, LocalDate> result = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getAssignedMinutes() <= 0 || assignment.getTimeSlot() == null) {
                continue;
            }
            LocalDate day = assignment.getTimeSlot().getDate();
            result.merge(assignment.getOperationKey(), day, (left, right) -> left.isBefore(right) ? left : right);
        }
        return result;
    }
}
