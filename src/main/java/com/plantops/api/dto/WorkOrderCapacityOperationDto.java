package com.plantops.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderCapacityOperationDto(
        String operationId,
        String operationName,
        int sequenceNo,
        String resourceId,
        List<String> allowedResourceIds,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs,
        int durationMinutes
) {
}
