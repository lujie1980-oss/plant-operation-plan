package com.plantops.ontology.scheduling;

import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodTimeSlotAlignmentTest {

    @Test
    void detectsMisalignedSlotId() {
        TimeSlot horizon = new TimeSlot(
                "RES-1-D0", 0, LocalDate.of(2026, 6, 1), "DAY", "RES-1", 480);
        SchedulingSlot ontology = new SchedulingSlot(
                "RES-1-D1", 0, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                TimeslotGranularity.DAY, "DAY", "RES-1", 480);

        assertFalse(PeriodTimeSlotAlignment.isAligned(List.of(ontology), List.of(horizon)));
        assertThrows(AssertionError.class, () ->
                PeriodTimeSlotAlignment.assertAligned(List.of(ontology), List.of(horizon)));
    }

    @Test
    void passesWhenListsMatch() {
        TimeSlot horizon = new TimeSlot(
                "RES-1-D0", 0, LocalDate.of(2026, 6, 1), "DAY", "RES-1", 480);
        SchedulingSlot ontology = SchedulingSlot.fromTimeSlot(horizon);

        assertTrue(PeriodTimeSlotAlignment.isAligned(List.of(ontology), List.of(horizon)));
        PeriodTimeSlotAlignment.assertAligned(List.of(ontology), List.of(horizon));
    }
}
