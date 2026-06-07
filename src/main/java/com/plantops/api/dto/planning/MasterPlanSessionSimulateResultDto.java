package com.plantops.api.dto.planning;

import java.util.List;

public record MasterPlanSessionSimulateResultDto(
        List<String> recalculatedPeriodIds,
        List<PispPeriodSnapshotDto> snapshots) {
}
