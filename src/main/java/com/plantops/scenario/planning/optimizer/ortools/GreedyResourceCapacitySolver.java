package com.plantops.scenario.planning.optimizer.ortools;

import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 产能贪心兜底：CP-SAT 全失败时按槽位剩余产能顺序填充。 */
public final class GreedyResourceCapacitySolver {

    private GreedyResourceCapacitySolver() {
    }

    public static boolean tryAssign(
            List<ResourceCapacityAssignment> toAssign,
            List<ResourceCapacityAssignment> allAssignments,
            MasterPlanSchedule schedule) {
        if (toAssign == null || toAssign.isEmpty()) {
            return true;
        }
        MasterPlanCapacityOverlay overlay = schedule.getCapacityOverlay() != null
                ? schedule.getCapacityOverlay()
                : MasterPlanCapacityOverlay.empty();

        Map<String, Integer> remainingSlotCapacity = new HashMap<>();
        for (TimeSlot slot : schedule.getTimeSlotRange()) {
            if (slot == null || !overlay.isSlotEligibleForReplan(slot)) {
                continue;
            }
            int capacity = Math.max(0, slot.getCapacityMinutes() - overlay.fixedMinutesForSlot(slot.getId()));
            remainingSlotCapacity.put(slot.getId(), capacity);
        }
        for (ResourceCapacityAssignment locked : allAssignments) {
            if (!locked.isLocked() || locked.getTimeSlot() == null || locked.getAssignedMinutes() <= 0) {
                continue;
            }
            String slotId = locked.getTimeSlot().getId();
            remainingSlotCapacity.computeIfPresent(
                    slotId, (id, cap) -> Math.max(0, cap - locked.getAssignedMinutes()));
        }

        Map<String, List<ResourceCapacityAssignment>> byOperation = new LinkedHashMap<>();
        for (ResourceCapacityAssignment assignment : toAssign) {
            byOperation.computeIfAbsent(assignment.getOperationKey(), ignored -> new ArrayList<>())
                    .add(assignment);
        }

        for (Map.Entry<String, List<ResourceCapacityAssignment>> entry : byOperation.entrySet()) {
            List<ResourceCapacityAssignment> group = entry.getValue();
            int remainingMinutes = group.get(0).getOperationTotalMinutes()
                    - lockedMinutesForOperation(entry.getKey(), allAssignments);
            if (remainingMinutes < 0) {
                return false;
            }
            group.sort(Comparator
                    .comparingInt(ResourceCapacityAssignment::getResourcePriority)
                    .thenComparingInt(ResourceCapacityAssignment::getDaySegmentIndex));

            for (ResourceCapacityAssignment assignment : group) {
                TimeSlot chosen = null;
                int assigned = 0;
                if (remainingMinutes > 0) {
                    int maxLoad = assignment.getOperationTotalMinutes();
                    List<TimeSlot> orderedSlots = orderedEligibleSlots(assignment, overlay);
                    for (TimeSlot slot : orderedSlots) {
                        int slotRemaining = remainingSlotCapacity.getOrDefault(slot.getId(), 0);
                        int load = Math.min(remainingMinutes, maxLoad);
                        if (slotRemaining > 0) {
                            load = Math.min(load, slotRemaining);
                        }
                        if (load <= 0) {
                            continue;
                        }
                        chosen = slot;
                        assigned = load;
                        remainingMinutes -= load;
                        if (slotRemaining > 0) {
                            remainingSlotCapacity.put(slot.getId(), slotRemaining - load);
                        }
                        break;
                    }
                }
                if (chosen == null && remainingMinutes > 0) {
                    List<TimeSlot> orderedSlots = orderedEligibleSlots(assignment, overlay);
                    if (!orderedSlots.isEmpty()) {
                        TimeSlot slot = orderedSlots.get(0);
                        int maxLoad = assignment.getOperationTotalMinutes();
                        int load = Math.min(remainingMinutes, maxLoad);
                        chosen = slot;
                        assigned = load;
                        remainingMinutes -= load;
                    }
                }
                if (chosen == null) {
                    chosen = firstEligibleSlot(assignment, overlay);
                    if (chosen == null) {
                        return false;
                    }
                }
                assignment.setTimeSlot(chosen);
                assignment.setAssignedMinutes(assigned);
            }
            if (remainingMinutes != 0) {
                return false;
            }
        }
        return true;
    }

    private static int lockedMinutesForOperation(
            String operationKey,
            List<ResourceCapacityAssignment> allAssignments) {
        int locked = 0;
        for (ResourceCapacityAssignment assignment : allAssignments) {
            if (!assignment.isLocked() || assignment.getTimeSlot() == null) {
                continue;
            }
            if (operationKey.equals(assignment.getOperationKey())) {
                locked += assignment.getAssignedMinutes();
            }
        }
        return locked;
    }

    private static List<TimeSlot> orderedEligibleSlots(
            ResourceCapacityAssignment assignment,
            MasterPlanCapacityOverlay overlay) {
        return assignment.getEligibleTimeSlots().stream()
                .filter(slot -> slot != null && overlay.isSlotEligibleForReplan(slot))
                .sorted(Comparator.comparing(TimeSlot::getDate).thenComparingInt(TimeSlot::getIndex))
                .toList();
    }

    private static TimeSlot firstEligibleSlot(
            ResourceCapacityAssignment assignment,
            MasterPlanCapacityOverlay overlay) {
        for (TimeSlot slot : orderedEligibleSlots(assignment, overlay)) {
            return slot;
        }
        return null;
    }
}
