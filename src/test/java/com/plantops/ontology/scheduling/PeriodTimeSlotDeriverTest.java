package com.plantops.ontology.scheduling;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodTimeSlotDeriverTest {

    @Test
    void derivesDailySlotsFromLeafPeriodsWithSrpCapacity() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<Period> periods = PeriodSequenceSpec.defaultSpec().expand(start);
        Map<String, StandardResourcePeriod> srps = new LinkedHashMap<>();
        for (Period period : periods) {
            StandardResourcePeriod srp = new StandardResourcePeriod(
                    OntologyIds.srpId("RES-A", period.getSequenceNr()),
                    "RES-A",
                    period.getId());
            srp.setTotalCapacity(480);
            srp.recalculateCapacityFields();
            srps.put(srp.getId(), srp);
        }

        List<TimeSlot> slots = PeriodTimeSlotDeriver.deriveTimeSlots(periods, srps, Set.of("RES-A"));

        assertEquals(28, slots.size());
        assertEquals(OntologyIds.schedulingSlotDayId("RES-A", 0), slots.get(0).getId());
        assertEquals(480, slots.get(0).getCapacityMinutes());
        assertEquals(TimeslotGranularity.DAY, slots.get(0).getGranularity());
        assertEquals("DAY", slots.get(0).getShiftId());
    }

    @Test
    void derivesShiftSlotsFromLeafShiftPeriods() {
        LocalDate start = LocalDate.of(2026, 6, 5);
        List<Period> periods = PeriodSequenceSpec.parse("1x2shift").expand(start);
        Map<String, StandardResourcePeriod> srps = new LinkedHashMap<>();
        for (Period period : periods) {
            StandardResourcePeriod srp = new StandardResourcePeriod(
                    OntologyIds.srpId("RES-SH", period.getSequenceNr()),
                    "RES-SH",
                    period.getId());
            if (period.isLeaf()) {
                srp.setTotalCapacity(period.getShiftId().equals("S1") ? 480 : 360);
                srp.recalculateCapacityFields();
            }
            srps.put(srp.getId(), srp);
        }

        List<TimeSlot> slots = PeriodTimeSlotDeriver.deriveTimeSlots(periods, srps, Set.of("RES-SH"));

        assertEquals(2, slots.size());
        assertEquals("S1", slots.get(0).getShiftId());
        assertEquals("S2", slots.get(1).getShiftId());
        assertEquals(480, slots.get(0).getCapacityMinutes());
        assertTrue(slots.get(0).getId().startsWith("RES-SH-SH"));
    }

    @Test
    void graphDeriveMatchesExplicitDerive() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        Period period = new Period(OntologyIds.periodId(0), 0, start, start);
        StandardResourcePeriod srp = new StandardResourcePeriod(
                OntologyIds.srpId("RES-B", 0), "RES-B", period.getId());
        srp.setTotalCapacity(400);
        srp.recalculateCapacityFields();
        OntologyGraph graph = OntologyGraph.builder()
                .periodsOrdered(List.of(period))
                .standardResourcePeriod(srp)
                .build();

        List<TimeSlot> fromGraph = PeriodTimeSlotDeriver.deriveTimeSlots(graph, Set.of("RES-B"));
        List<TimeSlot> explicit =
                PeriodTimeSlotDeriver.deriveTimeSlots(List.of(period), Map.of(srp.getId(), srp), Set.of("RES-B"));

        assertEquals(explicit.size(), fromGraph.size());
        for (int i = 0; i < explicit.size(); i++) {
            TimeSlot expected = explicit.get(i);
            TimeSlot actual = fromGraph.get(i);
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getIndex(), actual.getIndex());
            assertEquals(expected.getCapacityMinutes(), actual.getCapacityMinutes());
            assertEquals(expected.getShiftId(), actual.getShiftId());
        }
    }
}
