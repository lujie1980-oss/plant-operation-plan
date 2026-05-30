package com.plantops.scenario.planning.diagnostics;

import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.api.dto.planning.PlanningDiagnosticIssue;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningDiagnosticsSummarizerTest {

    @Test
    void masterPlanOneLineIncludesFunnelCounts() {
        MasterPlanPlanningDiagnosticsDto dto = new MasterPlanPlanningDiagnosticsDto(
                LocalDateTime.now(),
                MasterPlanCapacityStrategy.UNCONSTRAINED.name(),
                false,
                "snap-1",
                Map.of(
                        PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED, 10,
                        PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE, 8,
                        PlanningDiagnosticCodes.MP_WORK_ORDERS_SKIPPED_NOT_SCHEDULABLE, 1,
                        PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS, 2,
                        PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK, 3),
                List.of(new PlanningDiagnosticIssue("WARN", PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK, "WO-1", null, "m")),
                false);

        String line = PlanningDiagnosticsSummarizer.masterPlanOneLine(dto);
        assertTrue(line.contains("工单 10"));
        assertTrue(line.contains("求解分配 8"));
        assertTrue(line.contains("时窗回退 3"));
    }
}
