package com.plantops.api.dto;

import java.util.List;

public record MasterPlanStrategyDetailDto(
        String id,
        String name,
        String capacityStrategy,
        boolean isDefault,
        List<MasterPlanObjectiveDto> objectives) {
}
