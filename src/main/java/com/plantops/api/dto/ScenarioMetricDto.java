package com.plantops.api.dto;

public record ScenarioMetricDto(
        String metricId,
        String label,
        String unit,
        String chartType) {
}
