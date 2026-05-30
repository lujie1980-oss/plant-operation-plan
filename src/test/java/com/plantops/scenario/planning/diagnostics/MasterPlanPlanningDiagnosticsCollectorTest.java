package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterPlanPlanningDiagnosticsCollectorTest {

    @Test
    void skipIncrementsCounterAndRecordsIssue() {
        MasterPlanPlanningDiagnosticsCollector collector = new MasterPlanPlanningDiagnosticsCollector();
        collector.recordSkip(
                PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE,
                "WO-1",
                null,
                "不可排程");

        MasterPlanPlanningDiagnosticsDto dto = collector.toDto(MasterPlanCapacityStrategy.UNCONSTRAINED, false);

        assertEquals(1, dto.counters().get(PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE));
        assertEquals(1, dto.issues().size());
        assertEquals("SKIP", dto.issues().getFirst().severity());
        assertEquals(PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE, dto.issues().getFirst().reasonCode());
        assertFalse(dto.issuesTruncated());
    }

    @Test
    void warnDoesNotIncrementSkipCounters() {
        MasterPlanPlanningDiagnosticsCollector collector = new MasterPlanPlanningDiagnosticsCollector();
        collector.increment(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK);
        collector.recordWarn(
                PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK,
                "WO-2",
                "WO-2@OP1_0#0",
                "回退");

        MasterPlanPlanningDiagnosticsDto dto = collector.toDto(MasterPlanCapacityStrategy.FINITE_CAPACITY, true);

        assertEquals(1, dto.counters().get(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK));
        assertEquals(0, dto.counters().get(PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE));
        assertEquals("FINITE_CAPACITY", dto.capacityStrategy());
        assertTrue(dto.overlayActive());
    }

    @Test
    void issuesTruncatedWhenCapReached() {
        MasterPlanPlanningDiagnosticsCollector collector = new MasterPlanPlanningDiagnosticsCollector(2);
        collector.recordWarn(PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS, "A", "id1", "m1");
        collector.recordWarn(PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS, "B", "id2", "m2");
        collector.recordWarn(PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS, "C", "id3", "m3");

        MasterPlanPlanningDiagnosticsDto dto = collector.toDto(MasterPlanCapacityStrategy.UNCONSTRAINED, false);

        assertEquals(2, dto.issues().size());
        assertTrue(dto.issuesTruncated());
    }
}
