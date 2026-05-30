package com.plantops.scenario;

import com.plantops.config.ParameterRegistry;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 规划时栅：默认近端按日、远端按周；参数可配置。
 */
@ApplicationScoped
public class TimeslotHorizonService {

    public static final String MODE_DAILY_ONLY = "DAILY_ONLY";
    public static final String MODE_DAILY_THEN_WEEKLY = "DAILY_THEN_WEEKLY";
    public static final String SHIFT_WEEK = "WEEK";

    private static final int DEFAULT_SHIFT_MINUTES = 480;

    @Inject
    ParameterRegistry parameters;

    public TimeslotHorizonConfig config() {
        String mode = parameters.get("timeslot_granularity_mode");
        if (mode == null || mode.isBlank()) {
            mode = MODE_DAILY_THEN_WEEKLY;
        }
        int dailyDays = Math.max(1, parameters.getInt("timeslot_daily_days", 28));
        int weeklyBuckets = Math.max(0, parameters.getInt("timeslot_weekly_buckets", 6));
        int planningHorizonDays = Math.max(1, parameters.getInt("planning_horizon_days", 70));
        int totalDays;
        if (MODE_DAILY_ONLY.equalsIgnoreCase(mode)) {
            totalDays = planningHorizonDays;
            weeklyBuckets = 0;
        } else {
            int splitTotal = dailyDays + weeklyBuckets * 7;
            totalDays = Math.max(planningHorizonDays, splitTotal);
            if (totalDays > splitTotal) {
                int extraDays = totalDays - splitTotal;
                weeklyBuckets += (extraDays + 6) / 7;
                totalDays = dailyDays + weeklyBuckets * 7;
            }
        }
        return new TimeslotHorizonConfig(mode, dailyDays, weeklyBuckets, totalDays);
    }

    public int totalCalendarDays() {
        return config().totalCalendarDays();
    }

    public List<TimeSlot> buildSlots(LocalDate start, Set<String> resourceIds) {
        TimeslotHorizonConfig cfg = config();
        List<TimeSlot> slots = new ArrayList<>();
        int index = 0;
        for (String resourceId : resourceIds) {
            if (MODE_DAILY_ONLY.equalsIgnoreCase(cfg.mode())) {
                for (int d = 0; d < cfg.totalCalendarDays(); d++) {
                    LocalDate date = start.plusDays(d);
                    int cap = capacityForDay(resourceId, date);
                    slots.add(new TimeSlot(
                            resourceId + "-D" + d,
                            index++,
                            date,
                            date,
                            TimeslotGranularity.DAY,
                            "DAY",
                            resourceId,
                            cap));
                }
                continue;
            }
            for (int d = 0; d < cfg.dailyDays(); d++) {
                LocalDate date = start.plusDays(d);
                int cap = capacityForDay(resourceId, date);
                slots.add(new TimeSlot(
                        resourceId + "-D" + d,
                        index++,
                        date,
                        date,
                        TimeslotGranularity.DAY,
                        "DAY",
                        resourceId,
                        cap));
            }
            for (int w = 0; w < cfg.weeklyBuckets(); w++) {
                LocalDate weekStart = start.plusDays(cfg.dailyDays() + (long) w * 7);
                LocalDate weekEnd = weekStart.plusDays(6);
                int cap = capacityForRange(resourceId, weekStart, weekEnd);
                slots.add(new TimeSlot(
                        resourceId + "-W" + w,
                        index++,
                        weekStart,
                        weekEnd,
                        TimeslotGranularity.WEEK,
                        SHIFT_WEEK,
                        resourceId,
                        cap));
            }
        }
        return slots;
    }

    /**
     * 产能分析用：与求解器一致的 (date, shiftId) 桶列表（不含资源维度）。
     */
    public List<BucketKey> bucketKeys(LocalDate start) {
        TimeslotHorizonConfig cfg = config();
        List<BucketKey> keys = new ArrayList<>();
        if (MODE_DAILY_ONLY.equalsIgnoreCase(cfg.mode())) {
            for (int d = 0; d < cfg.totalCalendarDays(); d++) {
                LocalDate date = start.plusDays(d);
                keys.add(new BucketKey(date, "DAY", date, date, TimeslotGranularity.DAY));
            }
            return keys;
        }
        for (int d = 0; d < cfg.dailyDays(); d++) {
            LocalDate date = start.plusDays(d);
            keys.add(new BucketKey(date, "DAY", date, date, TimeslotGranularity.DAY));
        }
        for (int w = 0; w < cfg.weeklyBuckets(); w++) {
            LocalDate weekStart = start.plusDays(cfg.dailyDays() + (long) w * 7);
            LocalDate weekEnd = weekStart.plusDays(6);
            keys.add(new BucketKey(weekStart, SHIFT_WEEK, weekStart, weekEnd, TimeslotGranularity.WEEK));
        }
        return keys;
    }

    /**
     * 主计划/产能分析用：某日可用产能（分钟）。
     * <ul>
     *   <li>无产线或单产线：优先产线 ID 日历，否则生产资源 ID 日历，再否则产线默认班产能/全局默认班产能</li>
     *   <li>同一生产资源下多产线：各产线日历（resourceId=lineId）之和，不再使用资源级日历行（避免重复计量）</li>
     * </ul>
     */
    public int capacityForDay(String resourceId, LocalDate date) {
        if (resourceId == null || resourceId.isBlank() || date == null) {
            return defaultShiftMinutes();
        }
        List<ProductionLineEntity> lines = ProductionLineEntity.findByResourceId(resourceId);
        if (lines.size() > 1) {
            int sum = 0;
            for (ProductionLineEntity line : lines) {
                sum += capacityForLine(line, date);
            }
            return Math.max(0, sum);
        }
        if (lines.size() == 1) {
            ProductionLineEntity line = lines.get(0);
            if (hasCalendarOnOwner(line.lineId)) {
                return capacityForLine(line, date);
            }
            return capacityForCalendarOwner(resourceId, defaultLineCapacity(line), date);
        }
        return capacityForCalendarOwner(resourceId, defaultShiftMinutes(), date);
    }

    private int capacityForLine(ProductionLineEntity line, LocalDate date) {
        return capacityForCalendarOwner(line.lineId, defaultLineCapacity(line), date);
    }

    private static int defaultLineCapacity(ProductionLineEntity line) {
        return line != null && line.lineCapacityPerShift > 0
                ? line.lineCapacityPerShift
                : DEFAULT_SHIFT_MINUTES;
    }

    private int capacityForCalendarOwner(String ownerId, int fallbackMinutes, LocalDate date) {
        ResourceCalendarEntity cal = ResourceCalendarEntity
                .find("resourceId = ?1 and calendarDate = ?2", ownerId, date)
                .firstResult();
        return cal != null ? cal.availableCapacityMinutes : fallbackMinutes;
    }

    private boolean hasCalendarOnOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return false;
        }
        return ResourceCalendarEntity.count("resourceId = ?1", ownerId) > 0;
    }

    public int capacityForRange(String resourceId, LocalDate start, LocalDate endInclusive) {
        int sum = 0;
        LocalDate d = start;
        while (!d.isAfter(endInclusive)) {
            sum += capacityForDay(resourceId, d);
            d = d.plusDays(1);
        }
        return sum;
    }

    private int defaultShiftMinutes() {
        return parameters.getInt("shift_capacity_minutes", DEFAULT_SHIFT_MINUTES);
    }

    public record TimeslotHorizonConfig(
            String mode,
            int dailyDays,
            int weeklyBuckets,
            int totalCalendarDays) {
    }

    public record BucketKey(
            LocalDate bucketDate,
            String shiftId,
            LocalDate periodStart,
            LocalDate periodEnd,
            TimeslotGranularity granularity) {
    }
}
