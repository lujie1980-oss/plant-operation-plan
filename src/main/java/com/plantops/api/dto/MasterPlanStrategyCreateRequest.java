package com.plantops.api.dto;

import java.util.List;

public record MasterPlanStrategyCreateRequest(
        String name,
        String capacityStrategy,
        List<MasterPlanObjectiveUpdateDto> objectives) {
}
