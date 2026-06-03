package com.plantops.api.dto.planning;

public record DetailScheduleVersionSummaryDto(
        String planVersionId,
        String generatedAt,
        String score,
        Long solveDurationMs,
        int operationCount,
        int workOrderCount,
        int batchCount,
        int lineCount) {
}
