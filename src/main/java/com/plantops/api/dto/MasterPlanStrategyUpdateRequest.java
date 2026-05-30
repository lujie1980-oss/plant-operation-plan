package com.plantops.api.dto;

import java.util.List;

public record MasterPlanStrategyUpdateRequest(
        String name,
        String capacityStrategy,
        Boolean setAsDefault,
        List<MasterPlanObjectiveUpdateDto> objectives) {
}
