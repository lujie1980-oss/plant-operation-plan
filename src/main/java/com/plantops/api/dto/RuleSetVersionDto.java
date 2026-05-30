package com.plantops.api.dto;

public record RuleSetVersionDto(
        String ruleSetVersionId,
        String name,
        boolean isDefault,
        String createdAt,
        String updatedAt) {
}
