package com.plantops.solver.detailschedule;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排程锚点起的自然时间（连续分钟）换算；产线链式时间见 {@link LineChainTimingUtil}。
 */
public final class ScheduleTimingUtil {

    /** 排程锚点日起算的自然日分钟数（24h），用于契约日边界，非班次产能。 */
    public static final int MINUTES_PER_DAY = 24 * 60;

    private ScheduleTimingUtil() {
    }

    /** @deprecated 使用 {@link LineChainTimingUtil#applyAllStartTimes(DetailSchedule)} */
    @Deprecated
    public static void applyLineStartTimes(DetailSchedule schedule) {
        LineChainTimingUtil.applyAllStartTimes(schedule);
    }

    public static LocalDate completionDate(
            LocalDate planningAnchorDate,
            Integer startMinute,
            int durationMinutes) {
        if (planningAnchorDate == null || startMinute == null) {
            return null;
        }
        return planningAnchorDate.atStartOfDay()
                .plusMinutes((long) startMinute + Math.max(1, durationMinutes))
                .toLocalDate();
    }

    public static LocalDate startDate(LocalDate planningAnchorDate, Integer startMinute) {
        if (planningAnchorDate == null || startMinute == null) {
            return null;
        }
        return planningAnchorDate.atStartOfDay().plusMinutes(startMinute).toLocalDate();
    }

    public static LocalDateTime startDateTime(LocalDate planningAnchorDate, int startMinute) {
        return planningAnchorDate.atStartOfDay().plusMinutes(startMinute);
    }

    public static LocalDateTime completionDateTime(
            LocalDate planningAnchorDate,
            int startMinute,
            int durationMinutes) {
        return planningAnchorDate.atStartOfDay()
                .plusMinutes((long) startMinute + Math.max(1, durationMinutes));
    }
}
