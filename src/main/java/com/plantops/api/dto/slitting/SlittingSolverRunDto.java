package com.plantops.api.dto.slitting;

import com.plantops.api.dto.PipelineRunLogLineDto;

import java.util.List;

public record SlittingSolverRunDto(
        String runId,
        String runType,
        String planVersionId,
        String masterNodeId,
        String sessionId,
        String status,
        String startedTs,
        String finishedTs,
        Long durationMs,
        String score,
        String summary,
        String errorMessage,
        List<PipelineRunLogLineDto> executionLog) {
}
