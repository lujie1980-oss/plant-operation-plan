package com.plantops.ontology.scheduling;

import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 本体侧规划槽位，字段与 {@link TimeSlot} 1:1 对齐。
 *
 * @deprecated ENT-SS 已废止（ADR-16 · TODO-23 S5）；请使用 {@link PeriodTimeSlotDeriver#deriveTimeSlots}。
 */
@Deprecated
public final class SchedulingSlot {

    private final String id;
    private final int index;
    private final LocalDate date;
    private final LocalDate periodEnd;
    private final TimeslotGranularity granularity;
    private final String shiftId;
    private final String resourceId;
    private final int capacityMinutes;

    public SchedulingSlot(
            String id,
            int index,
            LocalDate date,
            LocalDate periodEnd,
            TimeslotGranularity granularity,
            String shiftId,
            String resourceId,
            int capacityMinutes) {
        this.id = Objects.requireNonNull(id, "id");
        this.index = index;
        this.date = Objects.requireNonNull(date, "date");
        this.periodEnd = periodEnd != null ? periodEnd : date;
        this.granularity = granularity != null ? granularity : TimeslotGranularity.DAY;
        this.shiftId = shiftId;
        this.resourceId = resourceId;
        this.capacityMinutes = capacityMinutes;
    }

    public static SchedulingSlot fromTimeSlot(TimeSlot slot) {
        if (slot == null) {
            throw new IllegalArgumentException("slot required");
        }
        return new SchedulingSlot(
                slot.getId(),
                slot.getIndex(),
                slot.getDate(),
                slot.getPeriodEnd(),
                slot.getGranularity(),
                slot.getShiftId(),
                slot.getResourceId(),
                slot.getCapacityMinutes());
    }

    public TimeSlot toTimeSlot() {
        return new TimeSlot(id, index, date, periodEnd, granularity, shiftId, resourceId, capacityMinutes);
    }

    public String getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public TimeslotGranularity getGranularity() {
        return granularity;
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
