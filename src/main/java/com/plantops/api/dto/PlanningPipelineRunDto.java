package com.plantops.api.dto;

import com.plantops.api.dto.planning.PlanningPipelineRunDiagnosticsDto;

import java.util.List;

public record PlanningPipelineRunDto(
        String runId,
        String capacityStrategy,
        String strategyId,
        String strategyName,
        String status,
        String startedAt,
        String finishedAt,
        Long durationMs,
        String masterPlanVersionId,
        String detailPlanVersionId,
        String masterPlanScore,
        String errorMessage,
        List<PipelineRunLogLineDto> executionLog,
        PlanningPipelineRunDiagnosticsDto diagnostics) {
}
