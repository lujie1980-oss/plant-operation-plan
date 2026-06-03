package com.plantops.api.dto.planning;

import java.time.LocalDateTime;

public record SimulationProfileDto(
        String profileId,
        String name,
        String layer,
        String masterPlanVersionId,
        String configJson,
        boolean active,
        LocalDateTime updatedTs) {
}
