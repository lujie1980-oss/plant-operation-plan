package com.plantops.solver.masterplan;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 主计划槽位与 {@link java.time.LocalDateTime} 对齐（与 MasterPlanService 排程口径一致）。 */
public final class MasterPlanSlotTimes {

    public static final String SHIFT_WEEK = "WEEK";

    private MasterPlanSlotTimes() {
    }

    public static LocalDateTime slotStart(TimeSlot slot) {
        if (slot == null) {
            return null;
        }
        int hour = "S2".equals(slot.getShiftId()) || "NIGHT".equals(slot.getShiftId()) ? 16 : 8;
        return slot.getDate().atTime(hour, 0);
    }

    public static boolean startsBefore(TimeSlot slot, LocalDateTime threshold) {
        if (slot == null || threshold == null) {
            return false;
        }
        return slotStart(slot).isBefore(threshold);
    }

    public static int violationMinutes(TimeSlot slot, LocalDateTime threshold) {
        if (slot == null || threshold == null || !startsBefore(slot, threshold)) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(slotStart(slot), threshold);
        return (int) Math.max(1, minutes);
    }
}
