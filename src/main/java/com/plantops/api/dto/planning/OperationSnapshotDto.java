package com.plantops.api.dto.planning;

import java.time.LocalDateTime;

public record OperationSnapshotDto(
        String id,
        String supplyOrderId,
        int sequenceNr,
        int routingSequenceNo,
        String operationName,
        long productionDuration,
        long preprocessingTime,
        long postprocessingTime,
        int segmentIndex,
        boolean lastSegment,
        String parallelGroupId,
        boolean locked,
        LocalDateTime earliestPossibleStartOwn,
        LocalDateTime earliestPossibleEndOwn,
        LocalDateTime earliestPossibleStartTotal,
        LocalDateTime earliestPossibleEndTotal,
        LocalDateTime latestDesiredStart,
        LocalDateTime latestDesiredEnd,
        LocalDateTime plannedStartTotal,
        LocalDateTime plannedEndTotal,
        boolean infeasible) {
}
