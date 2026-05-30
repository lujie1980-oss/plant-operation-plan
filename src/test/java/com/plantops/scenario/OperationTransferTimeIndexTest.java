package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OperationTransferTimeIndexTest {

    @Test
    void lookup_returnsConfiguredTransferMinutes() {
        OperationTransferTimeIndex index = new OperationTransferTimeIndex(List.of(
                new OperationTransferTimeIndex.Rule("P-001", "裁线", "半成品", 30, 15),
                new OperationTransferTimeIndex.Rule("P-001", "半成品", "成品", 60, 30)));

        assertEquals(30, index.transferMinutes("P-001", "裁线", "半成品"));
        assertEquals(15, index.minTransferMinutes("P-001", "裁线", "半成品"));
        assertEquals(60, index.transferMinutes("P-001", "半成品", "成品"));
        assertEquals(0, index.transferMinutes("P-001", "成品", "包装"));
    }

    @Test
    void emptyIndexReturnsZero() {
        OperationTransferTimeIndex index = new OperationTransferTimeIndex(List.of());
        assertEquals(0, index.transferMinutes("P-001", "A", "B"));
        assertNull(index.lookup("P-001", "A", "B"));
    }
}
