package com.plantops.api.dto;

import java.time.LocalDateTime;

public record FulfillmentOperationDto(
        String operationId,
        String operationName,
        int sequenceNo,
        String resourceId,
        LocalDateTime startTs,
        LocalDateTime endTs,
        int durationMinutes,
        int utilizationPct
) {
}
