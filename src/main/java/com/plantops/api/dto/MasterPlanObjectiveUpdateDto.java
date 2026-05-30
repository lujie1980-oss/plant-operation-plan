package com.plantops.api.dto;

public record MasterPlanObjectiveUpdateDto(
        String id,
        boolean enabled,
        int weight) {
}
