package com.plantops.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class FactoryCalendarDtos {

    private FactoryCalendarDtos() {
    }

    public record FactoryCalendarPolicyDto(
            Long id,
            boolean saturdayWork,
            boolean sundayWork,
            String shiftMode,
            String shift1Start,
            String shift1End,
            String shift2Start,
            String shift2End,
            String shift3Start,
            String shift3End) {
    }

    public record FactoryShiftStateDto(
            String shiftId,
            String label,
            String start,
            String end,
            boolean open,
            int capacityMinutes) {
    }

    public record FactoryCalendarDayDto(
            LocalDate date,
            int dayOfWeek,
            boolean weekend,
            boolean hasOverride,
            boolean workDay,
            List<FactoryShiftStateDto> shifts,
            int openShiftCount,
            int totalCapacityMinutes,
            String status) {
    }

    public record FactoryCalendarMonthDto(
            int year,
            int month,
            FactoryCalendarPolicyDto policy,
            List<FactoryCalendarDayDto> days) {
    }

    public record FactoryDayOverrideRequest(
            LocalDate date,
            boolean shift1Open,
            boolean shift2Open,
            Boolean shift3Open,
            boolean clearOverride) {
    }

    public record FactoryCalendarSyncResultDto(
            int horizonDays,
            int resourceOwnerCount,
            LocalDate fromDate,
            LocalDate toDate) {
    }
}
