package com.plantops.api.dto;

import java.util.List;

public record KpiReportDto(
        List<KpiMetricDto> metrics
) {
}
