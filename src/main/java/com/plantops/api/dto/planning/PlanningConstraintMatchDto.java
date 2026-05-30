package com.plantops.api.dto.planning;

import java.util.List;

public record PlanningConstraintMatchDto(
        String identification,
        int hardScore,
        int softScore,
        List<String> indictedIds
) {
}
