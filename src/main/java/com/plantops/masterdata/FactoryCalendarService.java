package com.plantops.masterdata;

import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarSyncResultDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarDayDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarMonthDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarPolicyDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryDayOverrideRequest;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryShiftStateDto;
import com.plantops.persistence.entity.FactoryCalendarDayOverrideEntity;
import com.plantops.persistence.entity.FactoryCalendarPolicyEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.scenario.TimeslotHorizonService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FactoryCalendarService {

    public static final String MODE_TWO = "TWO";
    public static final String MODE_THREE = "THREE";
    public static final int DEFAULT_PER_SHIFT_MINUTES = 480;
    private static final List<String> ALL_SHIFT_IDS = List.of("S1", "S2", "S3");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Transactional
    public FactoryCalendarPolicyDto getPolicy() {
        return toPolicyDto(ensurePolicy());
    }

    @Transactional
    public FactoryCalendarPolicyDto savePolicy(FactoryCalendarPolicyDto dto) {
        FactoryCalendarPolicyEntity entity = ensurePolicy();
        applyPolicyDto(entity, dto);
        entity.persist();
        regenerateHorizonCalendars(LocalDate.now());
        return toPolicyDto(entity);
    }

    /** 将当前工厂日历规则写入规划时栅内全部资源/产线的资源日历（可用产能）。 */
    @Transactional
    public FactoryCalendarSyncResultDto syncResourceCalendarsToHorizon() {
        LocalDate start = LocalDate.now();
        int days = timeslotHorizonService.totalCalendarDays();
        regenerateHorizonCalendars(start);
        return new FactoryCalendarSyncResultDto(
                days,
                collectCalendarResourceIds().size(),
                start,
                start.plusDays(Math.max(0, days - 1L)));
    }

    @Transactional
    public FactoryCalendarMonthDto getMonth(int year, int month) {
        FactoryCalendarPolicyEntity policy = ensurePolicy();
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<LocalDate, FactoryCalendarDayOverrideEntity> overrides = loadOverrides(from, to);
        List<FactoryCalendarDayDto> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(buildDayDto(d, policy, overrides.get(d)));
        }
        return new FactoryCalendarMonthDto(year, month, toPolicyDto(policy), days);
    }

    @Transactional
    public FactoryCalendarDayDto saveDayOverride(FactoryDayOverrideRequest request) {
        FactoryCalendarPolicyEntity policy = ensurePolicy();
        if (request.clearOverride()) {
            FactoryCalendarDayOverrideEntity existing =
                    FactoryCalendarDayOverrideEntity.findByDate(request.date());
            if (existing != null) {
                existing.delete();
            }
        } else {
            FactoryCalendarDayOverrideEntity entity =
                    FactoryCalendarDayOverrideEntity.findByDate(request.date());
            if (entity == null) {
                entity = new FactoryCalendarDayOverrideEntity();
                entity.calendarDate = request.date();
                entity.stampWorkspace();
            }
            entity.shift1Open = request.shift1Open();
            entity.shift2Open = request.shift2Open();
            entity.shift3Open = MODE_THREE.equalsIgnoreCase(policy.shiftMode) ? request.shift3Open() : null;
            entity.persist();
        }
        applyDayToResourceCalendars(request.date(), policy);
        FactoryCalendarDayOverrideEntity override =
                request.clearOverride() ? null : FactoryCalendarDayOverrideEntity.findByDate(request.date());
        return buildDayDto(request.date(), policy, override);
    }

    public FactoryCalendarPolicyEntity ensurePolicy() {
        FactoryCalendarPolicyEntity entity = FactoryCalendarPolicyEntity.findForWorkspace();
        if (entity != null) {
            return entity;
        }
        entity = defaultPolicy();
        entity.stampWorkspace();
        entity.persist();
        return entity;
    }

    private static FactoryCalendarPolicyEntity defaultPolicy() {
        FactoryCalendarPolicyEntity entity = new FactoryCalendarPolicyEntity();
        entity.saturdayWork = false;
        entity.sundayWork = false;
        entity.shiftMode = MODE_TWO;
        entity.shift1Start = LocalTime.of(8, 0);
        entity.shift1End = LocalTime.of(20, 0);
        entity.shift2Start = LocalTime.of(20, 0);
        entity.shift2End = LocalTime.of(8, 0);
        entity.shift3Start = LocalTime.of(0, 0);
        entity.shift3End = LocalTime.of(8, 0);
        return entity;
    }

    private void applyPolicyDto(FactoryCalendarPolicyEntity entity, FactoryCalendarPolicyDto dto) {
        entity.saturdayWork = dto.saturdayWork();
        entity.sundayWork = dto.sundayWork();
        entity.shiftMode = normalizeMode(dto.shiftMode());
        entity.shift1Start = parseTime(dto.shift1Start(), LocalTime.of(8, 0));
        entity.shift1End = parseTime(dto.shift1End(), LocalTime.of(20, 0));
        entity.shift2Start = parseTime(dto.shift2Start(), LocalTime.of(20, 0));
        entity.shift2End = parseTime(dto.shift2End(), LocalTime.of(8, 0));
        if (MODE_THREE.equalsIgnoreCase(entity.shiftMode)) {
            entity.shift3Start = parseTime(dto.shift3Start(), LocalTime.of(0, 0));
            entity.shift3End = parseTime(dto.shift3End(), LocalTime.of(8, 0));
        } else {
            entity.shift3Start = null;
            entity.shift3End = null;
        }
    }

    private FactoryCalendarDayDto buildDayDto(
            LocalDate date,
            FactoryCalendarPolicyEntity policy,
            FactoryCalendarDayOverrideEntity override) {
        boolean[] opens = resolveShiftOpens(date, policy, override);
        List<FactoryShiftStateDto> shifts = buildShiftStates(policy, opens);
        int openCount = (int) shifts.stream().filter(FactoryShiftStateDto::open).count();
        int totalMinutes = shifts.stream().filter(FactoryShiftStateDto::open).mapToInt(FactoryShiftStateDto::capacityMinutes).sum();
        DayOfWeek dow = date.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        String status;
        if (openCount == 0) {
            status = "CLOSED";
        } else if (openCount == shifts.size()) {
            status = "FULL";
        } else {
            status = "PARTIAL";
        }
        return new FactoryCalendarDayDto(
                date,
                dow.getValue(),
                weekend,
                override != null,
                openCount > 0,
                shifts,
                openCount,
                totalMinutes,
                status);
    }

    private boolean[] resolveShiftOpens(
            LocalDate date,
            FactoryCalendarPolicyEntity policy,
            FactoryCalendarDayOverrideEntity override) {
        int shiftCount = MODE_THREE.equalsIgnoreCase(policy.shiftMode) ? 3 : 2;
        boolean[] opens = new boolean[3];
        if (override != null) {
            opens[0] = override.shift1Open;
            opens[1] = override.shift2Open;
            opens[2] = override.shift3Open != null && override.shift3Open;
            return opens;
        }
        boolean workDay = isDefaultWorkDay(date, policy);
        for (int i = 0; i < shiftCount; i++) {
            opens[i] = workDay;
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

    private List<FactoryShiftStateDto> buildShiftStates(FactoryCalendarPolicyEntity policy, boolean[] opens) {
        List<FactoryShiftStateDto> shifts = new ArrayList<>();
        shifts.add(shiftState("S1", "早班", policy.shift1Start, policy.shift1End, opens[0]));
        shifts.add(shiftState("S2", "晚班", policy.shift2Start, policy.shift2End, opens[1]));
        if (MODE_THREE.equalsIgnoreCase(policy.shiftMode)) {
            shifts.add(shiftState("S3", "夜班", policy.shift3Start, policy.shift3End, opens[2]));
        }
        return shifts;
    }

    private FactoryShiftStateDto shiftState(
            String shiftId,
            String label,
            LocalTime start,
            LocalTime end,
            boolean open) {
        int minutes = open && start != null && end != null ? shiftDurationMinutes(start, end) : 0;
        return new FactoryShiftStateDto(
                shiftId,
                label,
                formatTime(start),
                formatTime(end),
                open,
                minutes);
    }

    static int shiftDurationMinutes(LocalTime start, LocalTime end) {
        int startMin = start.toSecondOfDay() / 60;
        int endMin = end.toSecondOfDay() / 60;
        if (endMin <= startMin) {
            endMin += 24 * 60;
        }
        return Math.max(0, endMin - startMin);
    }

    private Map<LocalDate, FactoryCalendarDayOverrideEntity> loadOverrides(LocalDate from, LocalDate to) {
        Map<LocalDate, FactoryCalendarDayOverrideEntity> map = new HashMap<>();
        for (FactoryCalendarDayOverrideEntity o : FactoryCalendarDayOverrideEntity.findBetween(from, to)) {
            map.put(o.calendarDate, o);
        }
        return map;
    }

    @Transactional
    void regenerateHorizonCalendars(LocalDate start) {
        int days = timeslotHorizonService.totalCalendarDays();
        FactoryCalendarPolicyEntity policy = ensurePolicy();
        Map<LocalDate, FactoryCalendarDayOverrideEntity> overrides = loadOverrides(
                start, start.plusDays(Math.max(0, days - 1L)));
        for (int d = 0; d < days; d++) {
            LocalDate date = start.plusDays(d);
            applyDayToResourceCalendars(date, policy, overrides.get(date));
        }
    }

    @Transactional
    void applyDayToResourceCalendars(LocalDate date, FactoryCalendarPolicyEntity policy) {
        FactoryCalendarDayOverrideEntity override = FactoryCalendarDayOverrideEntity.findByDate(date);
        applyDayToResourceCalendars(date, policy, override);
    }

    private void applyDayToResourceCalendars(
            LocalDate date,
            FactoryCalendarPolicyEntity policy,
            FactoryCalendarDayOverrideEntity override) {
        FactoryCalendarDayDto day = buildDayDto(date, policy, override);
        Set<String> activeShiftIds = day.shifts().stream().map(FactoryShiftStateDto::shiftId).collect(Collectors.toSet());
        Set<String> resourceIds = collectCalendarResourceIds();
        for (String resourceId : resourceIds) {
            int perShift = perShiftCapacityForOwner(resourceId);
            upsertDayCalendar(resourceId, date, perShift, day.shifts(), activeShiftIds);
        }
    }

    static int dailyCapacityForOwner(int perShiftCapacity, List<FactoryShiftStateDto> shifts) {
        int sum = 0;
        for (FactoryShiftStateDto shift : shifts) {
            if (shift.open()) {
                sum += perShiftCapacity;
            }
        }
        return sum;
    }

    private int perShiftCapacityForOwner(String ownerId) {
        ProductionLineEntity line = ProductionLineEntity.findByLineId(ownerId);
        if (line != null && line.lineCapacityPerShift > 0) {
            return line.lineCapacityPerShift;
        }
        return DEFAULT_PER_SHIFT_MINUTES;
    }

    private void upsertDayCalendar(
            String resourceId,
            LocalDate date,
            int perShiftCapacity,
            List<FactoryShiftStateDto> shifts,
            Set<String> activeShiftIds) {
        int totalMinutes = dailyCapacityForOwner(perShiftCapacity, shifts);
        ResourceCalendarEntity dayRow = ResourceCalendarEntity
                .find("workspaceId = ?1 and resourceId = ?2 and calendarDate = ?3 and shiftId = ?4",
                        ResourceCalendarEntity.ws(), resourceId, date, "DAY")
                .firstResult();
        if (dayRow == null) {
            dayRow = new ResourceCalendarEntity();
            dayRow.resourceId = resourceId;
            dayRow.shiftId = "DAY";
            dayRow.calendarDate = date;
            dayRow.unavailableCapacityMinutes = 0;
            dayRow.stampWorkspace();
        }
        dayRow.availableCapacityMinutes = totalMinutes;
        dayRow.persist();

        for (FactoryShiftStateDto shift : shifts) {
            ResourceCalendarEntity shiftRow = ResourceCalendarEntity
                    .find("workspaceId = ?1 and resourceId = ?2 and calendarDate = ?3 and shiftId = ?4",
                            ResourceCalendarEntity.ws(), resourceId, date, shift.shiftId())
                    .firstResult();
            if (shiftRow == null) {
                shiftRow = new ResourceCalendarEntity();
                shiftRow.resourceId = resourceId;
                shiftRow.shiftId = shift.shiftId();
                shiftRow.calendarDate = date;
                shiftRow.unavailableCapacityMinutes = 0;
                shiftRow.stampWorkspace();
            }
            shiftRow.availableCapacityMinutes = shift.open() ? perShiftCapacity : 0;
            shiftRow.persist();
        }

        for (String shiftId : ALL_SHIFT_IDS) {
            if (activeShiftIds.contains(shiftId)) {
                continue;
            }
            ResourceCalendarEntity stale = ResourceCalendarEntity
                    .find("workspaceId = ?1 and resourceId = ?2 and calendarDate = ?3 and shiftId = ?4",
                            ResourceCalendarEntity.ws(), resourceId, date, shiftId)
                    .firstResult();
            if (stale != null) {
                stale.availableCapacityMinutes = 0;
                stale.persist();
            }
        }
    }

    private Set<String> collectCalendarResourceIds() {
        Set<String> resourceIds = new LinkedHashSet<>();
        for (ProductionResourceEntity res : ProductionResourceEntity.listInWorkspace()) {
            if (res.bottleneck) {
                resourceIds.add(res.resourceId);
            }
        }
        for (ProductResourceEntity pr : ProductResourceEntity.listInWorkspace()) {
            resourceIds.add(pr.resourceId);
        }
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.lineId != null && !line.lineId.isBlank()) {
                resourceIds.add(line.lineId);
            }
        }
        return resourceIds;
    }

    private static FactoryCalendarPolicyDto toPolicyDto(FactoryCalendarPolicyEntity entity) {
        return new FactoryCalendarPolicyDto(
                entity.id,
                entity.saturdayWork,
                entity.sundayWork,
                entity.shiftMode,
                formatTime(entity.shift1Start),
                formatTime(entity.shift1End),
                formatTime(entity.shift2Start),
                formatTime(entity.shift2End),
                formatTime(entity.shift3Start),
                formatTime(entity.shift3End));
    }

    private static String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME_FMT);
    }

    private static LocalTime parseTime(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return LocalTime.parse(raw.length() == 5 ? raw : raw.substring(0, 5), TIME_FMT);
    }

    private static String normalizeMode(String mode) {
        return MODE_THREE.equalsIgnoreCase(mode) ? MODE_THREE : MODE_TWO;
    }
}
