package com.plantops.ontology.scheduling;

import com.plantops.solver.masterplan.TimeSlot;

import java.util.List;
import java.util.Objects;

/** 校验本体 {@link SchedulingSlot} 与求解 {@link TimeSlot} 列表是否对齐。 */
public final class PeriodTimeSlotAlignment {

    private PeriodTimeSlotAlignment() {
    }

    public static boolean isAligned(List<SchedulingSlot> ontologySlots, List<TimeSlot> horizonSlots) {
        if (ontologySlots == null || horizonSlots == null) {
            return false;
        }
        if (ontologySlots.size() != horizonSlots.size()) {
            return false;
        }
        for (int i = 0; i < ontologySlots.size(); i++) {
            if (!slotEquals(ontologySlots.get(i), horizonSlots.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static void assertAligned(List<SchedulingSlot> ontologySlots, List<TimeSlot> horizonSlots) {
        if (ontologySlots == null || horizonSlots == null) {
            throw new IllegalArgumentException("slot lists required");
        }
        if (ontologySlots.size() != horizonSlots.size()) {
            throw new AssertionError("slot count mismatch: ontology="
                    + ontologySlots.size() + " horizon=" + horizonSlots.size());
        }
        for (int i = 0; i < ontologySlots.size(); i++) {
            SchedulingSlot ontology = ontologySlots.get(i);
            TimeSlot horizon = horizonSlots.get(i);
            if (!slotEquals(ontology, horizon)) {
                throw new AssertionError("slot mismatch at index " + i
                        + ": ontology=" + ontology.getId() + " horizon=" + horizon.getId());
            }
        }
    }

    private static boolean slotEquals(SchedulingSlot ontology, TimeSlot horizon) {
        return Objects.equals(ontology.getId(), horizon.getId())
                && ontology.getIndex() == horizon.getIndex()
                && Objects.equals(ontology.getDate(), horizon.getDate())
                && Objects.equals(ontology.getPeriodEnd(), horizon.getPeriodEnd())
                && ontology.getGranularity() == horizon.getGranularity()
                && Objects.equals(ontology.getShiftId(), horizon.getShiftId())
                && Objects.equals(ontology.getResourceId(), horizon.getResourceId())
                && ontology.getCapacityMinutes() == horizon.getCapacityMinutes();
    }
}
