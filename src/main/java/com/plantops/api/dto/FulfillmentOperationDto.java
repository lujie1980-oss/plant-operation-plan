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
        int utilizationPct,
        String planUnitId,
        Integer planUnitSequenceNr,
        LocalDateTime earliestPossibleStartTotal,
        LocalDateTime latestDesiredEnd
) {
    public FulfillmentOperationDto(
            String operationId,
            String operationName,
            int sequenceNo,
            String resourceId,
            LocalDateTime startTs,
            LocalDateTime endTs,
            int durationMinutes,
            int utilizationPct) {
        this(
                operationId,
                operationName,
                sequenceNo,
                resourceId,
                startTs,
                endTs,
                durationMinutes,
                utilizationPct,
                null,
                null,
                null,
                null);
    }
}
