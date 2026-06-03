package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.SessionStepPatchDto;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Session 内手动调整：改产线、队列顺序、锁定。 */
public final class DetailScheduleSessionMutation {

    private DetailScheduleSessionMutation() {
    }

    public static List<String> applyPatches(DetailSchedule schedule, List<SessionStepPatchDto> patches) {
        if (patches == null || patches.isEmpty()) {
            return List.of();
        }
        Map<String, OperationAssignment> byId = indexById(schedule);
        List<String> touched = new ArrayList<>();
        for (SessionStepPatchDto patch : patches) {
            if (patch == null || patch.stepId() == null || patch.stepId().isBlank()) {
                throw new BadRequestException("stepId required in patch");
            }
            OperationAssignment op = byId.get(patch.stepId());
            if (op == null) {
                throw new BadRequestException("Unknown step: " + patch.stepId());
            }
            if (patch.pinned() != null) {
                op.setPinned(patch.pinned());
            }
            if (patch.lineId() != null) {
                if (patch.lineId().isBlank()) {
                    removeFromAllLines(schedule, op);
                } else {
                    ScheduleLine target = requireLine(schedule, patch.lineId());
                    removeFromAllLines(schedule, op);
                    insertOnLine(target, op, patch.sequenceOnLine());
                }
            } else if (patch.sequenceOnLine() != null) {
                ScheduleLine line = findLineForOp(schedule, op);
                if (line == null) {
                    throw new BadRequestException("sequenceOnLine requires line assignment: " + patch.stepId());
                }
                reorderOnLine(line, op, patch.sequenceOnLine());
            }
            touched.add(patch.stepId());
        }
        return touched;
    }

    private static void removeFromAllLines(DetailSchedule schedule, OperationAssignment op) {
        if (schedule.getLines() != null) {
            for (ScheduleLine line : schedule.getLines()) {
                if (line.getAssignedOperations() != null) {
                    line.getAssignedOperations().remove(op);
                }
            }
        }
        op.setLine(null);
        op.setStartMinute(null);
    }

    private static ScheduleLine requireLine(DetailSchedule schedule, String lineId) {
        if (schedule.getLines() == null) {
            throw new BadRequestException("No lines in schedule");
        }
        for (ScheduleLine line : schedule.getLines()) {
            if (lineId.equals(line.getLineId())) {
                return line;
            }
        }
        throw new BadRequestException("Unknown line: " + lineId);
    }

    private static void insertOnLine(ScheduleLine line, OperationAssignment op, Integer sequenceOnLine) {
        List<OperationAssignment> queue = line.getAssignedOperations();
        if (queue == null) {
            queue = new ArrayList<>();
            line.setAssignedOperations(queue);
        } else {
            queue = new ArrayList<>(queue);
            line.setAssignedOperations(queue);
        }
        int index = sequenceOnLine != null ? sequenceOnLine - 1 : queue.size();
        index = Math.max(0, Math.min(index, queue.size()));
        queue.add(index, op);
        op.setLine(line);
    }

    private static void reorderOnLine(ScheduleLine line, OperationAssignment op, int sequenceOnLine) {
        List<OperationAssignment> queue = line.getAssignedOperations();
        if (queue == null || !queue.contains(op)) {
            throw new BadRequestException("Operation not on line: " + op.getOperationId());
        }
        List<OperationAssignment> copy = new ArrayList<>(queue);
        copy.remove(op);
        int index = Math.max(0, Math.min(sequenceOnLine - 1, copy.size()));
        copy.add(index, op);
        line.setAssignedOperations(copy);
    }

    private static ScheduleLine findLineForOp(DetailSchedule schedule, OperationAssignment op) {
        if (op.getLine() != null) {
            return op.getLine();
        }
        if (schedule.getLines() == null) {
            return null;
        }
        for (ScheduleLine line : schedule.getLines()) {
            if (line.getAssignedOperations() != null && line.getAssignedOperations().contains(op)) {
                return line;
            }
        }
        return null;
    }

    private static Map<String, OperationAssignment> indexById(DetailSchedule schedule) {
        Map<String, OperationAssignment> map = new HashMap<>();
        if (schedule.getOperations() != null) {
            for (OperationAssignment op : schedule.getOperations()) {
                if (op.getOperationId() != null) {
                    map.put(op.getOperationId(), op);
                }
            }
        }
        return map;
    }
}
