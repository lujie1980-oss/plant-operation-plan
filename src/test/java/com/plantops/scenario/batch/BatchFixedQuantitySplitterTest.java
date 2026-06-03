package com.plantops.scenario.batch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchFixedQuantitySplitterTest {

    private static void assertQty(String expected, BigDecimal actual) {
        assertTrue(
                new BigDecimal(expected).compareTo(actual) == 0,
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void separateTail_splitsFullAndRemainder() {
        List<BigDecimal> parts = BatchFixedQuantitySplitter.split(
                new BigDecimal("100"),
                new BigDecimal("30"),
                BatchRemainderMode.SEPARATE_TAIL);
        assertEquals(4, parts.size());
        assertQty("30", parts.get(0));
        assertQty("10", parts.get(3));
    }

    @Test
    void mergeTail_mergesLastBatch() {
        List<BigDecimal> parts = BatchFixedQuantitySplitter.split(
                new BigDecimal("100"),
                new BigDecimal("30"),
                BatchRemainderMode.MERGE_TAIL);
        assertEquals(3, parts.size());
        assertQty("40", parts.get(2));
    }

    @Test
    void floor_ignoresRemainder() {
        List<BigDecimal> parts = BatchFixedQuantitySplitter.split(
                new BigDecimal("100"),
                new BigDecimal("30"),
                BatchRemainderMode.FLOOR);
        assertEquals(3, parts.size());
    }

    @Test
    void ceil_createsPartialLastBatch() {
        List<BigDecimal> parts = BatchFixedQuantitySplitter.split(
                new BigDecimal("25"),
                new BigDecimal("30"),
                BatchRemainderMode.CEIL);
        assertEquals(1, parts.size());
        assertQty("25", parts.get(0));
    }
}
