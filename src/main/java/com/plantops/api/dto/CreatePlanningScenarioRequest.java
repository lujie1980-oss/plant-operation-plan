package com.plantops.api.dto;

public record CreatePlanningScenarioRequest(
        String name,
        String strategyId,
        String ruleSetVersionId) {
}
