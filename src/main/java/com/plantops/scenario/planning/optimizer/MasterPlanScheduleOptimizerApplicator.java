package com.plantops.scenario.planning.optimizer;

import ai.timefold.solver.core.api.score.HardSoftScore;
import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将 {@link OptimizerResult} 写回 {@link MasterPlanSchedule}（非 Timefold 引擎路径）。 */
public final class MasterPlanScheduleOptimizerApplicator {

    private MasterPlanScheduleOptimizerApplicator() {
    }

    public static MasterPlanSchedule apply(MasterPlanSchedule schedule, OptimizerResult result) {
        if (schedule == null || result == null) {
            return schedule;
        }
        Map<String, MasterPlanAllocationDto> byAllocationId = new LinkedHashMap<>();
        for (MasterPlanAllocationDto dto : result.persistAllocations()) {
            byAllocationId.put(dto.allocationId(), dto);
        }
        List<TimeSlot> slots = schedule.getTimeSlotRange();
        for (OrderAllocation allocation : schedule.getOrderAllocations()) {
            MasterPlanAllocationDto dto = byAllocationId.get(allocation.getId());
            if (dto == null) {
                continue;
            }
            TimeSlot slot = resolveSlot(slots, dto.resourceId(), dto.slotDate(), dto.slotIndex(), dto.shiftId());
            if (slot != null) {
                allocation.setTimeSlot(slot);
            }
        }
        schedule.setScore(parseScore(result.scoreSummary()));
        return schedule;
    }

    private static TimeSlot resolveSlot(
            List<TimeSlot> slots,
            String resourceId,
            LocalDate slotDate,
            int slotIndex,
            String shiftId) {
        if (slots == null || resourceId == null || slotDate == null) {
            return null;
        }
        for (TimeSlot slot : slots) {
            if (slot.getIndex() == slotIndex
                    && resourceId.equals(slot.getResourceId())
                    && slotDate.equals(slot.getDate())
                    && (shiftId == null || shiftId.equals(slot.getShiftId()))) {
                return slot;
            }
        }
        return null;
    }

    private static HardSoftScore parseScore(String scoreSummary) {
        if (scoreSummary == null || scoreSummary.isBlank()) {
            return HardSoftScore.ZERO;
        }
        try {
            return HardSoftScore.parseScore(scoreSummary);
        } catch (RuntimeException ex) {
            return HardSoftScore.ZERO;
        }
    }
}
