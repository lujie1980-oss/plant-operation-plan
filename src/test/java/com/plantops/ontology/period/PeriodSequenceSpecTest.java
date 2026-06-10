package com.plantops.ontology.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeriodSequenceSpecTest {

    @Test
    void parsesMixedSpecAndExpandsPeriods() {
        PeriodSequenceSpec spec = PeriodSequenceSpec.parse("2x1d,1x1w,1x1m");
        List<Period> periods = spec.expand(LocalDate.of(2026, 6, 1));
        assertEquals(4, periods.size());
        // 2 daily
        assertEquals(LocalDate.of(2026, 6, 1), periods.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 1), periods.get(0).getEndDate());
        assertEquals(LocalDate.of(2026, 6, 2), periods.get(1).getStartDate());
        // 1 weekly: 6/3 – 6/9
        assertEquals(LocalDate.of(2026, 6, 3), periods.get(2).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 9), periods.get(2).getEndDate());
        // 1 monthly(30d): 6/10 – 7/9
        assertEquals(LocalDate.of(2026, 6, 10), periods.get(3).getStartDate());
        assertEquals(LocalDate.of(2026, 7, 9), periods.get(3).getEndDate());
        // sequenceNr 连续
        assertEquals(3, periods.get(3).getSequenceNr());
    }

    @Test
    void defaultSpecIs28Daily() {
        List<Period> periods = PeriodSequenceSpec.defaultSpec().expand(LocalDate.of(2026, 6, 1));
        assertEquals(28, periods.size());
        assertEquals(periods.get(5).getStartDate(), periods.get(5).getEndDate());
    }

    @Test
    void lengthMultiplierProducesMultiDayBuckets() {
        List<Period> periods = PeriodSequenceSpec.parse("2x2w").expand(LocalDate.of(2026, 6, 1));
        assertEquals(2, periods.size());
        // first 14-day bucket: 6/1 – 6/14
        assertEquals(LocalDate.of(2026, 6, 1), periods.get(0).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 14), periods.get(0).getEndDate());
        // second 14-day bucket: 6/15 – 6/28
        assertEquals(LocalDate.of(2026, 6, 15), periods.get(1).getStartDate());
        assertEquals(LocalDate.of(2026, 6, 28), periods.get(1).getEndDate());
    }

    @Test
    void strictParseRejectsInvalidSpec() {
        assertThrows(IllegalArgumentException.class, () -> PeriodSequenceSpec.parse("garbage"));
        assertThrows(IllegalArgumentException.class, () -> PeriodSequenceSpec.parse("1x0d"));
        assertThrows(IllegalArgumentException.class, () -> PeriodSequenceSpec.parse("0x1d"));
    }

    @Test
    void invalidSpecFallsBackToDefault() {
        assertEquals(28, PeriodSequenceSpec.parseOrDefault("garbage").expand(LocalDate.now()).size());
        assertEquals(28, PeriodSequenceSpec.parseOrDefault(null).expand(LocalDate.now()).size());
    }
}
