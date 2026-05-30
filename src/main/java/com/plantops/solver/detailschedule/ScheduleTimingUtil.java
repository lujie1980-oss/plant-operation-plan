package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与 {@link com.plantops.scenario.DetailScheduleService#assignStartTimes} 一致的产线内顺序堆叠逻辑。
 */
public final class ScheduleTimingUtil {

    private ScheduleTimingUtil() {
    }

    public static void applyLineStartTimes(DetailSchedule schedule) {
        if (schedule == null || schedule.getOperations() == null) {
            return;
        }
        ChangeoverRuleIndex changeoverRules = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().changeoverRules()
                : ChangeoverRuleIndex.fromWorkspace();
        LocalDate planningAnchorDate = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().planningAnchorDate()
                : LocalDate.now();
        int shiftCapacityMinutes = schedule.getProblemFacts() != null
                ? schedule.getProblemFacts().shiftCapacityMinutes()
                : 480;
        Map<String, OperationAssignment> byOperationId = new HashMap<>();
        for (OperationAssignment op : schedule.getOperations()) {
            byOperationId.put(op.getOperationId(), op);
        }

        Map<String, List<OperationAssignment>> byLine = new HashMap<>();
        for (OperationAssignment op : schedule.getOperations()) {
            if (op.getLine() == null) {
                op.setStartMinute(null);
                continue;
            }
            byLine.computeIfAbsent(op.getLine().getLineId(), k -> new ArrayList<>()).add(op);
        }
        for (List<OperationAssignment> lineOps : byLine.values()) {
            lineOps.sort(Comparator.comparingInt(OperationAssignment::getSequenceHint));
            int cursor = 0;
            OperationAssignment previous = null;
            Set<String> placedPairGroups = new HashSet<>();
            for (OperationAssignment op : lineOps) {
                if (op.getPairGroupId() != null && placedPairGroups.contains(op.getPairGroupId())) {
                    continue;
                }
                if (op.isParallelPaired() && op.getPairMateOperationId() != null) {
                    OperationAssignment mate = byOperationId.get(op.getPairMateOperationId());
                    if (mate != null
                            && mate.getLine() != null
                            && op.getLine() != null
                            && mate.getLine().getLineId().equals(op.getLine().getLineId())) {
                        if (previous != null && shouldApplyChangeover(previous, op)) {
                            cursor += changeoverRules.computeMinutes(
                                    op.getOperationName(),
                                    previous.getProductCode(),
                                    op.getProductCode());
                        }
                        cursor = Math.max(cursor, contractStartMinute(op, mate, planningAnchorDate, shiftCapacityMinutes));
                        op.setStartMinute(cursor);
                        mate.setStartMinute(cursor);
                        int span = Math.max(op.getDurationMinutes(), mate.getDurationMinutes());
                        cursor += span;
                        placedPairGroups.add(op.getPairGroupId());
                        previous = laterFinishing(op, mate);
                        continue;
                    }
                }
                if (previous != null && shouldApplyChangeover(previous, op)) {
                    cursor += changeoverRules.computeMinutes(
                            op.getOperationName(),
                            previous.getProductCode(),
                            op.getProductCode());
                }
                cursor = Math.max(cursor, contractStartMinute(op, planningAnchorDate, shiftCapacityMinutes));
                op.setStartMinute(cursor);
                cursor += op.getDurationMinutes();
                previous = op;
            }
        }
        for (OperationAssignment op : schedule.getOperations()) {
            if (op.getLine() == null) {
                op.setStartMinute(null);
            }
        }
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

    private static boolean shouldApplyChangeover(OperationAssignment previous, OperationAssignment next) {
        if (previous.getProductCode() == null || next.getProductCode() == null) {
            return false;
        }
        return !previous.getProductCode().equals(next.getProductCode());
    }

    /** 由相对分钟推算完成日（按班次容量折算到天）。 */
    public static LocalDate completionDate(
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes,
            Integer startMinute,
            int durationMinutes) {
        if (planningAnchorDate == null || startMinute == null) {
            return null;
        }
        int cap = Math.max(1, shiftCapacityMinutes);
        int endMinute = startMinute + Math.max(1, durationMinutes);
        int dayOffset = Math.max(0, (endMinute - 1) / cap);
        return planningAnchorDate.plusDays(dayOffset);
    }

    /** 由相对分钟推算开始日（按班次容量折算到天）。 */
    public static LocalDate startDate(
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes,
            Integer startMinute) {
        if (planningAnchorDate == null || startMinute == null) {
            return null;
        }
        int cap = Math.max(1, shiftCapacityMinutes);
        int dayOffset = Math.max(0, startMinute / cap);
        return planningAnchorDate.plusDays(dayOffset);
    }

    private static int contractStartMinute(
            OperationAssignment op,
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes) {
        if (op == null || op.getMpContractStartDate() == null || planningAnchorDate == null) {
            return 0;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(planningAnchorDate, op.getMpContractStartDate());
        if (days <= 0) {
            return 0;
        }
        return Math.toIntExact(Math.min((long) Integer.MAX_VALUE, days * Math.max(1, shiftCapacityMinutes)));
    }

    private static int contractStartMinute(
            OperationAssignment left,
            OperationAssignment right,
            LocalDate planningAnchorDate,
            int shiftCapacityMinutes) {
        return Math.max(
                contractStartMinute(left, planningAnchorDate, shiftCapacityMinutes),
                contractStartMinute(right, planningAnchorDate, shiftCapacityMinutes));
    }
}
