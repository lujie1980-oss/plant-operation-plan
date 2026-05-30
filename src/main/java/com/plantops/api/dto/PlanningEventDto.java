package com.plantops.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record PlanningEventDto(
        String eventId,
        String eventType,
        LocalDateTime eventTs,
        Map<String, Object> payload
) {
}
