package com.plantops.ontology.period;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodIndexTest {

    @Test
    void mapsDateIntoOwningBucketAndClampsEdges() {
        List<Period> periods = PeriodSequenceSpec.parse("2x1d,1x1w").expand(LocalDate.of(2026, 6, 1));
        PeriodIndex index = PeriodIndex.of(periods);
        assertEquals(0, index.sequenceFor(LocalDate.of(2026, 6, 1)));
        assertEquals(1, index.sequenceFor(LocalDate.of(2026, 6, 2)));
        assertEquals(2, index.sequenceFor(LocalDate.of(2026, 6, 5)));   // 周桶中段
        assertEquals(0, index.sequenceFor(LocalDate.of(2026, 5, 20)));  // 早于首桶 → 0
        assertEquals(2, index.sequenceFor(LocalDate.of(2026, 12, 31))); // 晚于末桶 → last
        assertEquals(0, index.sequenceFor(null));                       // null → 0
    }
}
