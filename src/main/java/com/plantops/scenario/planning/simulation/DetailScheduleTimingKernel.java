package com.plantops.scenario.planning.simulation;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;
import com.plantops.scenario.planning.simulation.timing.ChangeoverTimingRule;
import com.plantops.scenario.planning.simulation.timing.ContractEarliestTimingRule;
import com.plantops.scenario.planning.simulation.timing.RoutingChainTimingRule;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class DetailScheduleTimingKernel {

    private static final int MAX_ROUTING_ITERATIONS = 16;

    @Inject
    SimulationRuleRegistry registry;

    public void applyAllStartTimes(DetailSchedule schedule) {
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.FULL, Set.of());
        applyAllStartTimes(ctx, schedule);
    }

    public void applyAllStartTimes(SimulationRuleContext ctx, DetailSchedule schedule) {
        if (schedule == null || schedule.getOperations() == null) {
            return;
        }
        OperationTransferTimeIndex transferRules = RoutingChainTimingRule.transferRules(ctx);
        Map<String, OperationAssignment> byOperationId = indexById(schedule.getOperations());

        for (int iteration = 0; iteration < MAX_ROUTING_ITERATIONS; iteration++) {
            applyLineQueuesOnce(ctx, schedule, transferRules, byOperationId);
            if (!RoutingChainTimingRule.bumpEarliestFromRoutingPredecessors(schedule, transferRules)) {
                break;
            }
        }
        RoutingChainTimingRule.clampAssignedStartsToRoutingChain(schedule, transferRules);
        applyLineQueuesOnce(ctx, schedule, transferRules, byOperationId);

        Set<OperationAssignment> assigned = assignedOperations(schedule);
        for (OperationAssignment op : schedule.getOperations()) {
            if (!assigned.contains(op)) {
                op.setStartMinute(null);
            }
        }
    }

    public Map<String, Integer> changeoverMinutesBeforeByOperationId(DetailSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (schedule == null || schedule.getLines() == null) {
            return result;
        }
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.FULL, Set.of());
        ChangeoverRuleIndex changeoverRules = ctx.facts() != null
                ? ctx.facts().changeoverRules()
                : ChangeoverRuleIndex.fromWorkspace();
        Map<String, OperationAssignment> byOperationId = indexById(schedule.getOperations());
        for (ScheduleLine line : schedule.getLines()) {
            collectChangeoverForLine(ctx, line, changeoverRules, byOperationId, result);
        }
        return result;
    }

    private void applyLineQueuesOnce(
            SimulationRuleContext ctx,
            DetailSchedule schedule,
            OperationTransferTimeIndex transferRules,
            Map<String, OperationAssignment> byOperationId) {
        if (schedule.getLines() == null) {
            return;
        }
        for (ScheduleLine line : schedule.getLines()) {
            applySingleLineQueue(ctx, line, transferRules, byOperationId);
            for (var rule : registry.enabledTimingRules(ctx)) {
                rule.afterLineQueuePass(ctx, line);
            }
        }
    }

    private void applySingleLineQueue(
            SimulationRuleContext ctx,
            ScheduleLine line,
            OperationTransferTimeIndex transferRules,
            Map<String, OperationAssignment> byOperationId) {
        List<OperationAssignment> queue = line.getAssignedOperations();
        if (queue == null || queue.isEmpty()) {
            return;
        }
        boolean parallelEnabled = registry.isRuleTypeEnabled(
                ctx, com.plantops.masterdata.BusinessRuleTypeIds.PARALLEL_OPERATIONS);

        int cursor = 0;
        OperationAssignment previous = null;
        Set<String> placedPairGroups = new HashSet<>();

        for (OperationAssignment op : queue) {
            if (op.getPairGroupId() != null && placedPairGroups.contains(op.getPairGroupId())) {
                continue;
            }
            if (parallelEnabled
                    && op.isParallelPaired()
                    && op.getPairMateOperationId() != null) {
                OperationAssignment mate = byOperationId.get(op.getPairMateOperationId());
                if (mate != null && queue.contains(mate)) {
                    if (previous != null) {
                        cursor += registry.sumGapBeforeNext(ctx, previous, op, line);
                    }
                    cursor = Math.max(
                            cursor,
                            ContractEarliestTimingRule.effectiveEarliestStartMinute(op, mate, ctx));
                    cursor = Math.max(cursor, earliestFloor(ctx, op, transferRules));
                    cursor = Math.max(cursor, earliestFloor(ctx, mate, transferRules));
                    op.setStartMinute(cursor);
                    mate.setStartMinute(cursor);
                    int span = Math.max(op.getDurationMinutes(), mate.getDurationMinutes());
                    cursor += span;
                    placedPairGroups.add(op.getPairGroupId());
                    previous = laterFinishing(op, mate);
                    continue;
                }
            }

            if (previous != null) {
                cursor += registry.sumGapBeforeNext(ctx, previous, op, line);
            }
            cursor = Math.max(cursor, ContractEarliestTimingRule.effectiveEarliestStartMinute(op, ctx));
            cursor = Math.max(cursor, earliestFloor(ctx, op, transferRules));
            op.setStartMinute(cursor);
            cursor += op.getDurationMinutes();
            previous = op;
        }
    }

    private int earliestFloor(
            SimulationRuleContext ctx,
            OperationAssignment op,
            OperationTransferTimeIndex transferRules) {
        int floor = registry.maxEarliestFloorMinute(ctx, op);
        if (registry.isRuleTypeEnabled(
                ctx, com.plantops.masterdata.BusinessRuleTypeIds.OPERATION_TRANSFER_TIME)) {
            Integer routing = RoutingChainTimingRule.minimumStartRespectingRoutingChain(op, transferRules);
            if (routing != null) {
                floor = Math.max(floor, routing);
            }
        }
        return floor;
    }

    private static void collectChangeoverForLine(
            SimulationRuleContext ctx,
            ScheduleLine line,
            ChangeoverRuleIndex changeoverRules,
            Map<String, OperationAssignment> byOperationId,
            Map<String, Integer> result) {
        List<OperationAssignment> queue = line.getAssignedOperations();
        if (queue == null || queue.isEmpty()) {
            return;
        }
        OperationAssignment previous = null;
        Set<String> placedPairGroups = new HashSet<>();
        boolean parallelEnabled = true;

        for (OperationAssignment op : queue) {
            if (op.getPairGroupId() != null && placedPairGroups.contains(op.getPairGroupId())) {
                continue;
            }
            if (parallelEnabled
                    && op.isParallelPaired()
                    && op.getPairMateOperationId() != null) {
                OperationAssignment mate = byOperationId.get(op.getPairMateOperationId());
                if (mate != null && queue.contains(mate)) {
                    recordChangeoverBefore(changeoverRules, previous, op, result);
                    placedPairGroups.add(op.getPairGroupId());
                    previous = laterFinishing(op, mate);
                    continue;
                }
            }
            recordChangeoverBefore(changeoverRules, previous, op, result);
            previous = op;
        }
    }

    private static void recordChangeoverBefore(
            ChangeoverRuleIndex changeoverRules,
            OperationAssignment previous,
            OperationAssignment next,
            Map<String, Integer> result) {
        if (previous == null || next == null || next.getOperationId() == null) {
            return;
        }
        int minutes = ChangeoverTimingRule.changeoverGapMinutes(changeoverRules, previous, next);
        if (minutes > 0) {
            result.put(next.getOperationId(), minutes);
        }
    }

    private static Set<OperationAssignment> assignedOperations(DetailSchedule schedule) {
        Set<OperationAssignment> assigned = new HashSet<>();
        if (schedule.getLines() != null) {
            for (ScheduleLine line : schedule.getLines()) {
                if (line.getAssignedOperations() != null) {
                    assigned.addAll(line.getAssignedOperations());
                }
            }
        }
        return assigned;
    }

    private static OperationAssignment laterFinishing(OperationAssignment left, OperationAssignment right) {
        Integer leftEnd = left.getEndMinute();
        Integer rightEnd = right.getEndMinute();
        if (leftEnd == null) {
            return right;
        }
        if (rightEnd == null) {
            return left;
        }
        return rightEnd >= leftEnd ? right : left;
    }

    private static Map<String, OperationAssignment> indexById(List<OperationAssignment> operations) {
        Map<String, OperationAssignment> map = new HashMap<>();
        for (OperationAssignment op : operations) {
            if (op.getOperationId() != null) {
                map.put(op.getOperationId(), op);
            }
        }
        return map;
    }
}
