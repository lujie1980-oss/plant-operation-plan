package com.plantops.solver.masterplan;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 主计划槽位日历比较。
 * <p>
 * DAY 粒度槽位下，工序/BOM 先后按<strong>计划日</strong>比较（同日允许多道工序），
 * 分钟级比较仅用于槽内产能占用，不用于跨工序开工先后。
 */
public final class MasterPlanCalendarMoments {

    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    private MasterPlanCalendarMoments() {
    }

    public static int slotDayOrdinal(TimeSlot slot) {
        if (slot == null || slot.getDate() == null) {
            return Integer.MAX_VALUE;
        }
        return (int) ChronoUnit.DAYS.between(EPOCH, slot.getDate());
    }

    public static long slotStartMinutes(TimeSlot slot) {
        if (slot == null) {
            return 0;
        }
        return ChronoUnit.MINUTES.between(
                EPOCH.atStartOfDay(),
                MasterPlanSlotTimes.slotStart(slot));
    }

    public static long allocationStartMinutes(OrderAllocation allocation) {
        if (allocation == null || allocation.getTimeSlot() == null) {
            return Long.MIN_VALUE;
        }
        return slotStartMinutes(allocation.getTimeSlot());
    }

    public static long allocationEndMinutes(OrderAllocation allocation) {
        if (allocation == null || allocation.getTimeSlot() == null) {
            return Long.MIN_VALUE;
        }
        return slotStartMinutes(allocation.getTimeSlot()) + Math.max(0, allocation.getDurationMinutes());
    }

    public static int assignmentDayOrdinal(ResourceCapacityAssignment assignment) {
        if (assignment == null
                || assignment.getTimeSlot() == null
                || assignment.getAssignedMinutes() <= 0) {
            return Integer.MAX_VALUE;
        }
        return slotDayOrdinal(assignment.getTimeSlot());
    }

    public static boolean sameCalendarStart(TimeSlot a, TimeSlot b) {
        if (a == null || b == null) {
            return false;
        }
        return slotDayOrdinal(a) == slotDayOrdinal(b);
    }

    /** 工序/BOM：前项计划日晚于后项计划日则违反（同日合法）。 */
    public static boolean violatesPlanningDayOrder(LocalDate earlierAllowedLatest, LocalDate laterRequiredEarliest) {
        if (earlierAllowedLatest == null || laterRequiredEarliest == null) {
            return false;
        }
        return earlierAllowedLatest.isAfter(laterRequiredEarliest);
    }

    public static int violationDays(LocalDate earlierAllowedLatest, LocalDate laterRequiredEarliest) {
        if (!violatesPlanningDayOrder(earlierAllowedLatest, laterRequiredEarliest)) {
            return 0;
        }
        return (int) Math.max(1, ChronoUnit.DAYS.between(laterRequiredEarliest, earlierAllowedLatest));
    }
}
