package com.plantops.ontology.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodExpanderTest {

    @Test
    void expands14x3shiftTo42LeafShiftPeriods() {
        List<Period> periods = PeriodSequenceSpec.parse("14x3shift").expand(LocalDate.of(2026, 6, 1));

        assertEquals(56, periods.size());
        assertEquals(42, periods.stream().filter(Period::isLeaf).count());
        assertEquals(14, periods.stream().filter(p -> !p.isLeaf()).count());
        assertEquals(0, periods.get(0).getSequenceNr());
        assertEquals(41, periods.get(41).getSequenceNr());

        Period firstDayFirstShift = periods.get(1);
        assertEquals(LocalDate.of(2026, 6, 1), firstDayFirstShift.getStartDate());
        assertEquals("S1", firstDayFirstShift.getShiftId());
        assertNotNull(firstDayFirstShift.getStartDateTime());
        assertNotNull(firstDayFirstShift.getEndDateTime());
        assertEquals(periods.get(0).getId(), firstDayFirstShift.getParentPeriodId());

        Period firstDayLastShift = periods.get(3);
        assertEquals(LocalDate.of(2026, 6, 1), firstDayLastShift.getStartDate());
        assertEquals("S3", firstDayLastShift.getShiftId());

        Period secondDayFirstShift = periods.get(5);
        assertEquals(LocalDate.of(2026, 6, 2), secondDayFirstShift.getStartDate());
        assertEquals("S1", secondDayFirstShift.getShiftId());

        Period last = periods.get(55);
        assertEquals(LocalDate.of(2026, 6, 14), last.getStartDate());
        assertEquals("S3", last.getShiftId());
    }

    @Test
    void expandsMixedShiftDayWeekSpec() {
        List<Period> periods =
                PeriodSequenceSpec.parse("14x3shift,4x1d,2x1w").expand(LocalDate.of(2026, 6, 1));

        assertEquals(62, periods.size());
        assertEquals(48, periods.stream().filter(Period::isLeaf).count());
        assertEquals(42, periods.stream().filter(p -> p.getGranularity() == PeriodGranularity.SHIFT).count());
        assertEquals(18, periods.stream().filter(p -> p.getGranularity() == PeriodGranularity.DAY).count());
        assertEquals(4, periods.stream()
                .filter(p -> p.getGranularity() == PeriodGranularity.DAY && p.isLeaf())
                .count());
        assertEquals(2, periods.stream().filter(p -> p.getGranularity() == PeriodGranularity.WEEK).count());

        Period firstDayBucket = periods.get(56);
        assertEquals(LocalDate.of(2026, 6, 15), firstDayBucket.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 15), firstDayBucket.getEndDate());
        assertNull(firstDayBucket.getShiftId());

        Period firstWeek = periods.get(60);
        assertEquals(LocalDate.of(2026, 6, 19), firstWeek.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 25), firstWeek.getEndDate());
        assertEquals(PeriodGranularity.WEEK, firstWeek.getGranularity());
    }
}
