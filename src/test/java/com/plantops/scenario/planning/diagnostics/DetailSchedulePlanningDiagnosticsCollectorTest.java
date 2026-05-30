package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.solver.detailschedule.OperationAssignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailSchedulePlanningDiagnosticsCollectorTest {

    @Test
    void scanBindingFlagsCountsParallelAndContinuous() {
        DetailSchedulePlanningDiagnosticsCollector collector = new DetailSchedulePlanningDiagnosticsCollector();

        OperationAssignment paired = new OperationAssignment();
        paired.setOperationId("op1");
        paired.setWorkOrderNo("WO-1");
        paired.setProductCode("P1");
        paired.setDurationMinutes(10);
        paired.setParallelPaired(true);
        paired.setPairGroupId("G1");

        OperationAssignment orphan = new OperationAssignment();
        orphan.setOperationId("op2");
        orphan.setWorkOrderNo("WO-1");
        orphan.setParallelOrphan(true);

        OperationAssignment continuous = new OperationAssignment();
        continuous.setOperationId("op3");
        continuous.setWorkOrderNo("WO-2");
        continuous.setContinuousProduction(true);

        collector.scanBindingFlags(List.of(paired, orphan, continuous));

        DetailSchedulePlanningDiagnosticsDto dto = collector.toDto("MP-test");
        assertEquals(1, dto.counters().get(PlanningDiagnosticCodes.DS_PARALLEL_PAIRED_OPS));
        assertEquals(1, dto.counters().get(PlanningDiagnosticCodes.DS_PARALLEL_ORPHAN_OPS));
        assertEquals(1, dto.counters().get(PlanningDiagnosticCodes.DS_CONTINUOUS_PRODUCTION_OPS));
        assertEquals("MP-test", dto.masterPlanVersionId());
    }

    @Test
    void kittingWarnRecorded() {
        DetailSchedulePlanningDiagnosticsCollector collector = new DetailSchedulePlanningDiagnosticsCollector();
        collector.recordWarn(
                PlanningDiagnosticCodes.WO_KITTING_SHORT,
                "WO-K",
                null,
                "齐套不足");

        DetailSchedulePlanningDiagnosticsDto dto = collector.toDto(null);
        assertEquals(1, dto.issues().size());
        assertEquals(PlanningDiagnosticCodes.WO_KITTING_SHORT, dto.issues().getFirst().reasonCode());
        assertTrue(dto.issues().getFirst().message().contains("齐套"));
    }
}
