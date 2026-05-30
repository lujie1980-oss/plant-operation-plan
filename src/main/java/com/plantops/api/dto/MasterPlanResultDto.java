package com.plantops.api.dto;

import java.util.List;

public record MasterPlanResultDto(
        String planVersionId,
        String score,
        Long solveDurationMs,
        /** UNCONSTRAINED | FINITE_CAPACITY */
        String capacityStrategy,
        String strategyId,
        String strategyName,
        List<DemandPoolKpiDto> kpis,
        List<MasterPlanAllocationDto> allocations,
        List<LineOpeningDecisionDto> lineOpenings
) {
}
