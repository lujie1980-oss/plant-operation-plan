package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单工序开工时间（Timefold 2.0 declarative shadow supplier 逻辑）。 */
final class OperationStartTimeCalculator {

    private OperationStartTimeCalculator() {
    }

    static Integer compute(OperationAssignment op, DetailSchedule schedule) {
        if (schedule == null || op == null || op.getLine() == null) {
            return null;
        }
        LocalDate planningAnchor = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().planningAnchorDate()
                : LocalDate.now();
        ChangeoverRuleIndex changeoverRules = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().changeoverRules()
                : ChangeoverRuleIndex.fromWorkspace();
        OperationTransferTimeIndex transferRules = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().transferRules()
                : new OperationTransferTimeIndex(List.of());

        Map<String, OperationAssignment> byId = indexById(schedule.getOperations());
        if (op.isParallelPaired() && op.getPairMateOperationId() != null) {
            OperationAssignment mate = byId.get(op.getPairMateOperationId());
            if (mate != null && mate.getLine() != null && mate.getLine().getLineId().equals(op.getLine().getLineId())) {
                return computeSingle(op, mate, schedule, planningAnchor, changeoverRules, transferRules, byId);
            }
        }
        return computeSingle(op, null, schedule, planningAnchor, changeoverRules, transferRules, byId);
    }

    private static Integer computeSingle(
            OperationAssignment op,
            OperationAssignment parallelMate,
            DetailSchedule schedule,
            LocalDate planningAnchor,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules,
            Map<String, OperationAssignment> byId) {
        ScheduleContractSettings contractSettings = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().contractSettings()
                : ScheduleContractSettings.defaults();
        int start = effectiveEarliestStartMinute(op, parallelMate, planningAnchor, contractSettings);

        OperationAssignment previousOnLine = op.getPreviousOnLine();
        if (previousOnLine != null && previousOnLine.getStartMinute() != null) {
            start = Math.max(start, previousOnLine.getEndMinute()
                    + changeoverGapMinutes(changeoverRules, previousOnLine, op));
        }

        OperationAssignment routingPred = op.getRoutingPredecessor();
        if (routingPred != null) {
            Integer required = LineChainTimingUtil.minimumStartRespectingRoutingChain(op, transferRules);
            if (required != null) {
                start = Math.max(start, required);
            }
        }

        return start;
    }

    private static Map<String, OperationAssignment> indexById(List<OperationAssignment> operations) {
        Map<String, OperationAssignment> map = new HashMap<>();
        if (operations == null) {
            return map;
        }
        for (OperationAssignment op : operations) {
            if (op.getOperationId() != null) {
                map.put(op.getOperationId(), op);
            }
        }
        return map;
    }

    private static int changeoverGapMinutes(
            ChangeoverRuleIndex changeoverRules,
            OperationAssignment previous,
            OperationAssignment next) {
        if (changeoverRules == null
                || previous == null
                || next == null
                || previous.getProductCode() == null
                || next.getProductCode() == null
                || next.getOperationName() == null
                || next.getOperationName().isBlank()) {
            return 0;
        }
        return Math.max(
                0,
                changeoverRules.computeMinutes(
                        next.getOperationName(),
                        next.getResourceId(),
                        next.getOperationSeq(),
                        previous.getProductCode(),
                        next.getProductCode()));
    }

    private static int effectiveEarliestStartMinute(
            OperationAssignment op,
            OperationAssignment parallelMate,
            LocalDate planningAnchor,
            ScheduleContractSettings contractSettings) {
        if (parallelMate != null) {
            return Math.max(
                    effectiveEarliestStartMinute(op, planningAnchor, contractSettings),
                    effectiveEarliestStartMinute(parallelMate, planningAnchor, contractSettings));
        }
        return Math.max(
                op.getEarliestStartMinute(),
                contractSettings.contractStartMinuteFloor(op, planningAnchor));
    }

    private static int effectiveEarliestStartMinute(
            OperationAssignment op,
            LocalDate planningAnchor,
            ScheduleContractSettings contractSettings) {
        return Math.max(
                op.getEarliestStartMinute(),
                contractSettings.contractStartMinuteFloor(op, planningAnchor));
    }
}
