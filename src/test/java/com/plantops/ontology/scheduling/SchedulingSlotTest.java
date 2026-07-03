package com.plantops.ontology.scheduling;

import com.plantops.ontology.OntologyIds;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulingSlotTest {

    @Test
    void fromTimeSlotPreservesHorizonSlotIdFormat() {
        TimeSlot horizon = new TimeSlot(
                OntologyIds.schedulingSlotDayId("RES-1", 3),
                7,
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 4),
                TimeslotGranularity.DAY,
                "DAY",
                "RES-1",
                480);

        SchedulingSlot slot = SchedulingSlot.fromTimeSlot(horizon);
        assertEquals("RES-1-D3", slot.getId());
        assertEquals(7, slot.getIndex());
        assertEquals(480, slot.getCapacityMinutes());

        TimeSlot roundTrip = slot.toTimeSlot();
        assertEquals(horizon.getId(), roundTrip.getId());
        assertEquals(horizon.getIndex(), roundTrip.getIndex());
    }
}
