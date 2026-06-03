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
import java.util.OptionalInt;
import java.util.Set;

@ApplicationScoped
public class DetailScheduleTimingKernel {

    @Inject
    SimulationRuleRegistry registry;

    /**
     * Timefold shadow supplier：单工序链式下界，与 {@link #applyAllStartTimes} 共用规则插件。
     */
    public Integer computeShadowStartMinute(OperationAssignment op, DetailSchedule schedule) {
        if (schedule == null || op == null || op.getLine() == null) {
            return null;
        }
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.FULL, Set.of());
        Map<String, OperationAssignment> byId = indexById(schedule.getOperations());

        if (registry.isRuleTypeEnabled(ctx, com.plantops.masterdata.BusinessRuleTypeIds.PARALLEL_OPERATIONS)
                && op.isParallelPaired()
                && op.getPairMateOperationId() != null) {
            OperationAssignment mate = byId.get(op.getPairMateOperationId());
            if (mate != null
                    && mate.getLine() != null
                    && mate.getLine().getLineId().equals(op.getLine().getLineId())) {
                return computeShadowSingle(op, mate, ctx);
            }
        }
        return computeShadowSingle(op, null, ctx);
    }

    private int computeShadowSingle(
            OperationAssignment op,
            OperationAssignment parallelMate,
            SimulationRuleContext ctx) {
        ScheduleLine line = op.getLine();
        int start = parallelMate != null
                ? ContractEarliestTimingRule.effectiveEarliestStartMinute(op, parallelMate, ctx)
                : ContractEarliestTimingRule.effectiveEarliestStartMinute(op, ctx);
        start = Math.max(start, registry.maxEarliestFloorMinute(ctx, op));
        if (parallelMate != null) {
            start = Math.max(start, registry.maxEarliestFloorMinute(ctx, parallelMate));
        }

        OperationAssignment previousOnLine = op.getPreviousOnLine();
        if (previousOnLine != null && previousOnLine.getStartMinute() != null) {
            int afterPrev = previousOnLine.getStartMinute()
                    + previousOnLine.getDurationMinutes()
                    + registry.sumGapBeforeNext(ctx, previousOnLine, op, line);
            start = Math.max(start, afterPrev);
        }

        if (op.getRoutingPredecessor() != null
                && registry.isRuleTypeEnabled(
                        ctx, com.plantops.masterdata.BusinessRuleTypeIds.OPERATION_TRANSFER_TIME)) {
            Integer required = RoutingChainTimingRule.minimumStartRespectingRoutingChain(
                    op, RoutingChainTimingRule.transferRules(ctx));
            if (required != null) {
                start = Math.max(start, required);
            }
        }
        start = registry.snapStartMinute(ctx, op, line, start);
        return start;
    }

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

        int maxIterations = ctx.profileSettings().maxRoutingIterations();
        for (int iteration = 0; iteration < maxIterations; iteration++) {
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
            OptionalInt fixed = registry.fixedStartMinute(ctx, op);
            if (fixed.isPresent() && !(parallelEnabled
                    && op.isParallelPaired()
                    && op.getPairMateOperationId() != null)) {
                assignFixedStart(op, fixed.getAsInt());
                cursor = Math.max(cursor, endMinute(op));
                previous = op;
                continue;
            }
            if (parallelEnabled
                    && op.isParallelPaired()
                    && op.getPairMateOperationId() != null) {
                OperationAssignment mate = byOperationId.get(op.getPairMateOperationId());
                if (mate != null && queue.contains(mate)) {
                    OptionalInt mateFixed = registry.fixedStartMinute(ctx, mate);
                    if (mateFixed.isPresent() || fixed.isPresent()) {
                        int start = fixed.isPresent() ? fixed.getAsInt() : mateFixed.getAsInt();
                        if (fixed.isPresent() && mateFixed.isPresent()) {
                            start = Math.max(fixed.getAsInt(), mateFixed.getAsInt());
                        }
                        assignFixedStart(op, start);
                        assignFixedStart(mate, start);
                        cursor = Math.max(cursor, Math.max(endMinute(op), endMinute(mate)));
                        placedPairGroups.add(op.getPairGroupId());
                        previous = laterFinishing(op, mate);
                        continue;
                    }
                    if (previous != null) {
                        cursor += registry.sumGapBeforeNext(ctx, previous, op, line);
                    }
                    cursor = Math.max(
                            cursor,
                            ContractEarliestTimingRule.effectiveEarliestStartMinute(op, mate, ctx));
                    cursor = Math.max(cursor, earliestFloor(ctx, op, transferRules));
                    cursor = Math.max(cursor, earliestFloor(ctx, mate, transferRules));
                    cursor = registry.snapStartMinute(ctx, op, line, cursor);
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
            cursor = registry.snapStartMinute(ctx, op, line, cursor);
            op.setStartMinute(cursor);
            cursor += op.getDurationMinutes();
            previous = op;
        }
    }

    private static void assignFixedStart(OperationAssignment op, int start) {
        op.setStartMinute(start);
    }

    private static int endMinute(OperationAssignment op) {
        Integer end = op.getEndMinute();
        if (end != null) {
            return end;
        }
        Integer start = op.getStartMinute();
        return start != null ? start + op.getDurationMinutes() : 0;
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
