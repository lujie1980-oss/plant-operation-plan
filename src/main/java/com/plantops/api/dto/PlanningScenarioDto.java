package com.plantops.api.dto;

public record PlanningScenarioDto(
        String scenarioId,
        String name,
        boolean isDefault,
        String strategyId,
        String strategyName,
        String ruleSetVersionId,
        String ruleSetVersionName,
        String currentPlanVersionId,
        String previousPlanVersionId,
        String currentGeneratedAt,
        String currentScore,
        Long currentSolveDurationMs,
        String planVersionId,
        String runId,
        String label,
        String capacityStrategy,
        String generatedAt,
        String score,
        Long solveDurationMs) {
}
