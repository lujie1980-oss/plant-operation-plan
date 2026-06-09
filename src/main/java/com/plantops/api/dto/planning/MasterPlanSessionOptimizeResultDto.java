package com.plantops.api.dto.planning;

import java.util.List;

public record MasterPlanSessionOptimizeResultDto(
        String sessionId,
        String score,
        int allocationCount,
        long solveDurationMs,
        List<PispPeriodSnapshotDto> affectedSnapshots) {
}
