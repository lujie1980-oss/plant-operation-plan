package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统计多机台 RCA 方案相对日历产能的超负荷分钟数（软约束惩罚用）。 */
public final class ResourceCapacityOverloadCalculator {

    private ResourceCapacityOverloadCalculator() {
    }

    public static int totalOverloadMinutes(List<ResourceCapacityAssignment> assignments, MasterPlanSchedule schedule) {
        if (assignments == null || assignments.isEmpty() || schedule == null) {
            return 0;
        }
        MasterPlanCapacityOverlay overlay = schedule.getCapacityOverlay() != null
                ? schedule.getCapacityOverlay()
                : MasterPlanCapacityOverlay.empty();
        Map<String, Integer> usedBySlotId = new HashMap<>();
        Map<String, Integer> capacityBySlotId = new HashMap<>();
        for (TimeSlot slot : schedule.getTimeSlotRange()) {
            if (slot == null) {
                continue;
            }
            capacityBySlotId.put(
                    slot.getId(),
                    Math.max(0, slot.getCapacityMinutes() - overlay.fixedMinutesForSlot(slot.getId())));
        }
        for (ResourceCapacityAssignment assignment : assignments) {
            if (assignment.getAssignedMinutes() <= 0 || assignment.getTimeSlot() == null) {
                continue;
            }
            usedBySlotId.merge(assignment.getTimeSlot().getId(), assignment.getAssignedMinutes(), Integer::sum);
        }
        int overload = 0;
        for (Map.Entry<String, Integer> entry : usedBySlotId.entrySet()) {
            int capacity = capacityBySlotId.getOrDefault(entry.getKey(), 0);
            overload += Math.max(0, entry.getValue() - capacity);
        }
        return overload;
    }
}
