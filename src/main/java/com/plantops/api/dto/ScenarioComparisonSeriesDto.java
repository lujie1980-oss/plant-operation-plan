package com.plantops.api.dto;

public record ScenarioComparisonSeriesDto(
        String planVersionId,
        String scenarioLabel,
        String metricId,
        double value) {
}
