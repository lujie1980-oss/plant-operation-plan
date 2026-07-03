package com.plantops.scenario.planning.optimizer;

import com.plantops.api.dto.MasterPlanAllocationDto;

import java.util.List;

public record OptimizerResult(
        String engineId,
        List<PlanningAssignment> assignments,
        String scoreSummary,
        long solveDurationMs,
        List<PlanningDiagnostic> diagnostics,
        List<MasterPlanAllocationDto> persistAllocations) {

    public OptimizerResult(
            String engineId,
            List<PlanningAssignment> assignments,
            String scoreSummary,
            long solveDurationMs,
            List<PlanningDiagnostic> diagnostics) {
        this(engineId, assignments, scoreSummary, solveDurationMs, diagnostics, List.of());
    }

    public OptimizerResult {
        assignments = assignments == null ? List.of() : List.copyOf(assignments);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        persistAllocations = persistAllocations == null ? List.of() : List.copyOf(persistAllocations);
    }
}
