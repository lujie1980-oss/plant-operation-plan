package com.plantops.scenario;

import com.plantops.persistence.entity.FactoryCalendarDayOverrideEntity;
import com.plantops.persistence.entity.FactoryCalendarPolicyEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.solver.detailschedule.ScheduleTimingUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 资源可用班次窗口（相对排程锚点的分钟区间），供 Session 班次日历赋时规则使用。
 */
public final class ResourceWorkingCalendarIndex {

    public record MinuteWindow(int startMinuteInclusive, int endMinuteExclusive) {
    }

    private static final ResourceWorkingCalendarIndex EMPTY =
            new ResourceWorkingCalendarIndex(LocalDate.now(), Map.of());

    private final LocalDate anchorDate;
    private final Map<String, List<MinuteWindow>> windowsByResourceId;

    public ResourceWorkingCalendarIndex(LocalDate anchorDate, Map<String, List<MinuteWindow>> windowsByResourceId) {
        this.anchorDate = anchorDate != null ? anchorDate : LocalDate.now();
        this.windowsByResourceId = windowsByResourceId != null ? windowsByResourceId : Map.of();
    }

    public static ResourceWorkingCalendarIndex empty() {
        return EMPTY;
    }

    public static ResourceWorkingCalendarIndex fromWorkspace(LocalDate anchorDate, int horizonDays) {
        LocalDate anchor = anchorDate != null ? anchorDate : LocalDate.now();
        int days = Math.max(1, horizonDays);
        FactoryCalendarPolicyEntity policy = FactoryCalendarPolicyEntity.findForWorkspace();
        if (policy == null) {
            return empty();
        }
        Map<LocalDate, FactoryCalendarDayOverrideEntity> overrides = loadOverrides(
                anchor, anchor.plusDays(days - 1L));
        Map<String, List<MinuteWindow>> byResource = new HashMap<>();
        for (ResourceCalendarEntity row : ResourceCalendarEntity.listInWorkspace()) {
            if (row.resourceId == null || row.calendarDate == null || row.availableCapacityMinutes <= 0) {
                continue;
            }
            long dayOffset = java.time.temporal.ChronoUnit.DAYS.between(anchor, row.calendarDate);
            if (dayOffset < 0 || dayOffset >= days) {
                continue;
            }
            int dayBase = (int) dayOffset * ScheduleTimingUtil.MINUTES_PER_DAY;
            List<ShiftWindow> shiftWindows = resolveShiftWindows(policy, row.calendarDate, overrides.get(row.calendarDate));
            for (ShiftWindow shift : shiftWindows) {
                if (!shift.open() || !shift.shiftId().equals(row.shiftId)) {
                    continue;
                }
                if ("DAY".equals(row.shiftId)) {
                    for (ShiftWindow openShift : shiftWindows) {
                        if (openShift.open()) {
                            addWindow(byResource, row.resourceId, dayBase, openShift);
                        }
                    }
                } else {
                    addWindow(byResource, row.resourceId, dayBase, shift);
                }
            }
        }
        byResource.replaceAll((k, v) -> mergeWindows(v));
        return new ResourceWorkingCalendarIndex(anchor, Map.copyOf(byResource));
    }

    public int snapForward(String resourceId, int minute) {
        if (resourceId == null || resourceId.isBlank()) {
            return minute;
        }
        List<MinuteWindow> windows = windowsByResourceId.get(resourceId);
        if (windows == null || windows.isEmpty()) {
            return minute;
        }
        for (MinuteWindow window : windows) {
            if (minute >= window.startMinuteInclusive() && minute < window.endMinuteExclusive()) {
                return minute;
            }
            if (minute < window.startMinuteInclusive()) {
                return window.startMinuteInclusive();
            }
        }
        return minute;
    }

    public boolean hasCalendar(String resourceId) {
        return resourceId != null && windowsByResourceId.containsKey(resourceId);
    }

    private static void addWindow(
            Map<String, List<MinuteWindow>> byResource,
            String resourceId,
            int dayBase,
            ShiftWindow shift) {
        int startInDay = shift.startMinuteOfDay();
        int endInDay = shift.endMinuteOfDay();
        int start = dayBase + startInDay;
        int end = dayBase + endInDay;
        if (end <= start) {
            end += ScheduleTimingUtil.MINUTES_PER_DAY;
        }
        byResource.computeIfAbsent(resourceId, k -> new ArrayList<>())
                .add(new MinuteWindow(start, end));
    }

    private static List<MinuteWindow> mergeWindows(List<MinuteWindow> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        raw.sort(Comparator.comparingInt(MinuteWindow::startMinuteInclusive));
        List<MinuteWindow> merged = new ArrayList<>();
        MinuteWindow current = raw.get(0);
        for (int i = 1; i < raw.size(); i++) {
            MinuteWindow next = raw.get(i);
            if (next.startMinuteInclusive() <= current.endMinuteExclusive()) {
                current = new MinuteWindow(
                        current.startMinuteInclusive(),
                        Math.max(current.endMinuteExclusive(), next.endMinuteExclusive()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static Map<LocalDate, FactoryCalendarDayOverrideEntity> loadOverrides(LocalDate from, LocalDate to) {
        Map<LocalDate, FactoryCalendarDayOverrideEntity> map = new HashMap<>();
        for (FactoryCalendarDayOverrideEntity o : FactoryCalendarDayOverrideEntity.findBetween(from, to)) {
            map.put(o.calendarDate, o);
        }
        return map;
    }

    private record ShiftWindow(String shiftId, LocalTime start, LocalTime end, boolean open) {
        int startMinuteOfDay() {
            return start != null ? start.toSecondOfDay() / 60 : 0;
        }

        int endMinuteOfDay() {
            if (end == null) {
                return ScheduleTimingUtil.MINUTES_PER_DAY;
            }
            int endMin = end.toSecondOfDay() / 60;
            int startMin = startMinuteOfDay();
            if (endMin <= startMin) {
                endMin += ScheduleTimingUtil.MINUTES_PER_DAY;
            }
            return endMin;
        }
    }

    private static List<ShiftWindow> resolveShiftWindows(
            FactoryCalendarPolicyEntity policy,
            LocalDate date,
            FactoryCalendarDayOverrideEntity override) {
        boolean[] opens = resolveShiftOpens(policy, date, override);
        List<ShiftWindow> shifts = new ArrayList<>();
        shifts.add(new ShiftWindow("S1", policy.shift1Start, policy.shift1End, opens[0]));
        shifts.add(new ShiftWindow("S2", policy.shift2Start, policy.shift2End, opens[1]));
        if ("THREE".equalsIgnoreCase(policy.shiftMode)) {
            shifts.add(new ShiftWindow("S3", policy.shift3Start, policy.shift3End, opens[2]));
        }
        shifts.add(new ShiftWindow("DAY", LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, anyOpen(opens)));
        return shifts;
    }

    private static boolean[] resolveShiftOpens(
            FactoryCalendarPolicyEntity policy,
            LocalDate date,
            FactoryCalendarDayOverrideEntity override) {
        int shiftCount = "THREE".equalsIgnoreCase(policy.shiftMode) ? 3 : 2;
        boolean[] opens = new boolean[3];
        if (override != null) {
            opens[0] = override.shift1Open;
            opens[1] = override.shift2Open;
            opens[2] = override.shift3Open != null && override.shift3Open;
            return opens;
        }
        if (!isDefaultWorkDay(date, policy)) {
            return opens;
        }
        for (int i = 0; i < shiftCount; i++) {
            opens[i] = true;
        }
        return opens;
    }

    private static boolean isDefaultWorkDay(LocalDate date, FactoryCalendarPolicyEntity policy) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) {
            return policy.saturdayWork;
        }
        if (dow == DayOfWeek.SUNDAY) {
            return policy.sundayWork;
        }
        return true;
    }

    private static boolean anyOpen(boolean[] opens) {
        for (boolean open : opens) {
            if (open) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceWorkingCalendarIndex that)) {
            return false;
        }
        return Objects.equals(anchorDate, that.anchorDate) && Objects.equals(windowsByResourceId, that.windowsByResourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anchorDate, windowsByResourceId);
    }
}
