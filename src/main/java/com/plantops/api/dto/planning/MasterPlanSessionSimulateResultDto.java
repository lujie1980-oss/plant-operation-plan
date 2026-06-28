package com.plantops.api.dto.planning;

import java.util.List;

public record MasterPlanSessionSimulateResultDto(
        List<String> recalculatedPeriodIds,
        List<PispPeriodSnapshotDto> snapshots,
        List<SrpSnapshotDto> srpSnapshots,
        List<OperationSnapshotDto> operationSnapshots) {

    public MasterPlanSessionSimulateResultDto(
            List<String> recalculatedPeriodIds,
            List<PispPeriodSnapshotDto> snapshots) {
        this(recalculatedPeriodIds, snapshots, List.of(), List.of());
    }
}
