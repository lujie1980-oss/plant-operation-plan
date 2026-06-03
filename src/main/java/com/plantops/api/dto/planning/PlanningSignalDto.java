package com.plantops.api.dto.planning;

public record PlanningSignalDto(
        String severity,
        String reasonCode,
        String message,
        String entityId
) {
}
