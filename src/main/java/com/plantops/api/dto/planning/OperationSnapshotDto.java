package com.plantops.api.dto.planning;

import java.time.LocalDate;

public record OperationSnapshotDto(
        String id,
        String supplyOrderId,
        int sequenceNr,
        String operationName,
        double productionTimeMinutes,
        LocalDate earliestPossibleStart,
        LocalDate latestPossibleEnd,
        boolean infeasible) {
}
