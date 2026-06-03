package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DetailScheduleKpiServiceTest {

    @Test
    void parseOperationSeq_extractsSequenceFromOperationId() {
        assertEquals(2, DetailScheduleKpiService.parseOperationSeq("OP-WO-MRP-1-2411379-1-20260612-1-2_0"));
        assertEquals(1, DetailScheduleKpiService.parseOperationSeq("OP-BATCH-001-1_0"));
        assertEquals(-1, DetailScheduleKpiService.parseOperationSeq(null));
        assertEquals(-1, DetailScheduleKpiService.parseOperationSeq("invalid"));
    }
}
