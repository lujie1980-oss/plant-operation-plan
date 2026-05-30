package com.plantops.api.dto;

import java.time.LocalDateTime;

public record WorkOrderCapacityOperationDto(
        String operationId,
        String operationName,
        int sequenceNo,
        String resourceId,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs,
        int durationMinutes
) {
}
