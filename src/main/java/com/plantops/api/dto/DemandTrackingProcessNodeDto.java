package com.plantops.api.dto;

import java.time.LocalDateTime;

public record DemandTrackingProcessNodeDto(
        String nodeId,
        String nodeType,
        String label,
        String planStatus,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDateTime productionStart,
        LocalDateTime productionEnd,
        int sequenceNo
) {
}
