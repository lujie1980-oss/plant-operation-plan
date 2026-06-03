package com.plantops.solver.detailschedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 构造初始可行解：按 sequenceHint 将工序放入各产线 {@link ScheduleLine#getAssignedOperations()} list。
 */
public final class DetailScheduleLineInitializer {

    private DetailScheduleLineInitializer() {
    }

    public static void seedInitialQueues(DetailSchedule schedule) {
        if (schedule == null || schedule.getLines() == null || schedule.getOperations() == null) {
            return;
        }
        Map<String, ScheduleLine> lineById = new HashMap<>();
        for (ScheduleLine line : schedule.getLines()) {
            line.setAssignedOperations(new ArrayList<>());
            lineById.put(line.getLineId(), line);
        }

        Map<String, List<OperationAssignment>> pendingByLine = new HashMap<>();
        for (OperationAssignment op : schedule.getOperations()) {
            String lineId = resolveInitialLineId(op, lineById);
            if (lineId == null) {
                continue;
            }
            pendingByLine.computeIfAbsent(lineId, k -> new ArrayList<>()).add(op);
        }

        Set<String> placedPairGroups = new HashSet<>();
        for (Map.Entry<String, List<OperationAssignment>> entry : pendingByLine.entrySet()) {
            ScheduleLine line = lineById.get(entry.getKey());
            if (line == null) {
                continue;
            }
            List<OperationAssignment> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingInt(OperationAssignment::getSequenceHint));
            List<OperationAssignment> queue = new ArrayList<>();
            for (OperationAssignment op : sorted) {
                if (op.getPairGroupId() != null && placedPairGroups.contains(op.getPairGroupId())) {
                    continue;
                }
                queue.add(op);
                if (op.isParallelPaired() && op.getPairGroupId() != null) {
                    placedPairGroups.add(op.getPairGroupId());
                }
            }
            line.setAssignedOperations(queue);
        }
    }

    /**
     * 从持久化结果恢复 list 顺序（按 startMinute / sequenceIndex 排序）。
     */
    public static void restoreLineQueues(
            DetailSchedule schedule,
            Map<String, Integer> startMinuteByOperationId,
            Map<String, String> lineIdByOperationId) {
        if (schedule == null || schedule.getLines() == null) {
            return;
        }
        Map<String, ScheduleLine> lineById = new HashMap<>();
        for (ScheduleLine line : schedule.getLines()) {
            line.setAssignedOperations(new ArrayList<>());
            lineById.put(line.getLineId(), line);
        }
        Map<String, List<OperationAssignment>> byLine = new HashMap<>();
        for (OperationAssignment op : schedule.getOperations()) {
            String lineId = lineIdByOperationId.get(op.getOperationId());
            if (lineId == null) {
                continue;
            }
            byLine.computeIfAbsent(lineId, k -> new ArrayList<>()).add(op);
        }
        for (Map.Entry<String, List<OperationAssignment>> entry : byLine.entrySet()) {
            ScheduleLine line = lineById.get(entry.getKey());
            if (line == null) {
                continue;
            }
            List<OperationAssignment> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator
                    .comparingInt((OperationAssignment op) ->
                            startMinuteByOperationId.getOrDefault(op.getOperationId(), Integer.MAX_VALUE))
                    .thenComparing(OperationAssignment::getOperationId));
            line.setAssignedOperations(sorted);
        }
    }

    private static String resolveInitialLineId(
            OperationAssignment op,
            Map<String, ScheduleLine> lineById) {
        if (op.getDesignatedLineId() != null && lineById.containsKey(op.getDesignatedLineId())) {
            return op.getDesignatedLineId();
        }
        if (op.getAllowedLineIds() != null) {
            for (String lineId : op.getAllowedLineIds()) {
                ScheduleLine line = lineById.get(lineId);
                if (line != null && op.acceptsLine(line)) {
                    return lineId;
                }
            }
        }
        for (ScheduleLine line : lineById.values()) {
            if (line.isOpened() && op.acceptsLine(line)) {
                return line.getLineId();
            }
        }
        return null;
    }
}
