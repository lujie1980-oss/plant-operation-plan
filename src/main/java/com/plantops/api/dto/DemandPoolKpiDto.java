package com.plantops.api.dto;

public record DemandPoolKpiDto(
        String metricId,
        String label,
        double value,
        String unit,
        String severity
) {
}
