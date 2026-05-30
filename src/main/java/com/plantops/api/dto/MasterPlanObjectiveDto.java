package com.plantops.api.dto;

public record MasterPlanObjectiveDto(
        String id,
        String name,
        String description,
        String penaltyUnit,
        boolean enabled,
        int weight,
        int defaultWeight) {
}
