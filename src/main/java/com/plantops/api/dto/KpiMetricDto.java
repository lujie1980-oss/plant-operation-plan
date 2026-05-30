package com.plantops.api.dto;

public record KpiMetricDto(
        String metricId,
        double value,
        String unit
) {
}
