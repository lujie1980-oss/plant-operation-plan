package com.plantops.api.dto;

import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiBreakdownDto;

import java.util.List;

public record MasterPlanResultDto(
        String planVersionId,
        String score,
        Long solveDurationMs,
        /** UNCONSTRAINED | FINITE_CAPACITY */
        String capacityStrategy,
        String strategyId,
        String strategyName,
        Integer totalKpi,
        String scoreSummary,
        KpiBreakdownDto kpiBreakdown,
        List<DemandPoolKpiDto> kpis,
        List<MasterPlanAllocationDto> allocations,
        List<LineOpeningDecisionDto> lineOpenings
) {
}
