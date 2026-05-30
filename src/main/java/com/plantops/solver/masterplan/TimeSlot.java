package com.plantops.solver.masterplan;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.LocalDate;

public class TimeSlot {

    @PlanningId
    private String id;
    private int index;
    private LocalDate date;
    private LocalDate periodEnd;
    private TimeslotGranularity granularity;
    private String shiftId;
    private String resourceId;
    private int capacityMinutes;

    public TimeSlot() {
    }

    public TimeSlot(
            String id,
            int index,
            LocalDate date,
            String shiftId,
            String resourceId,
            int capacityMinutes) {
        this(id, index, date, date, TimeslotGranularity.DAY, shiftId, resourceId, capacityMinutes);
    }

    public TimeSlot(
            String id,
            int index,
            LocalDate periodStart,
            LocalDate periodEnd,
            TimeslotGranularity granularity,
            String shiftId,
            String resourceId,
            int capacityMinutes) {
        this.id = id;
        this.index = index;
        this.date = periodStart;
        this.periodEnd = periodEnd != null ? periodEnd : periodStart;
        this.granularity = granularity != null ? granularity : TimeslotGranularity.DAY;
        this.shiftId = shiftId;
        this.resourceId = resourceId;
        this.capacityMinutes = capacityMinutes;
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    /** 周期起始日（日槽=当天，周槽=周一或周起始日） */
    public LocalDate getDate() {
        return date;
    }

    /** 周期结束日（含） */
    public LocalDate getPeriodEnd() {
        return periodEnd != null ? periodEnd : date;
    }

    public TimeslotGranularity getGranularity() {
        return granularity != null ? granularity : TimeslotGranularity.DAY;
    }

    public boolean isWeekly() {
        return getGranularity() == TimeslotGranularity.WEEK;
    }

    public String getShiftId() {
        return shiftId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public int getCapacityMinutes() {
        return capacityMinutes;
    }
}
