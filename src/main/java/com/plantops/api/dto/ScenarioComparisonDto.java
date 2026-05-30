package com.plantops.api.dto;

import java.util.List;

public record ScenarioComparisonDto(
        List<ScenarioMetricDto> metrics,
        List<ScenarioComparisonSeriesDto> series) {
}
