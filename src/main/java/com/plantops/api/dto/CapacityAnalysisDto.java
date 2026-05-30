package com.plantops.api.dto;

import java.util.List;

public record CapacityAnalysisDto(
        List<DemandPoolKpiDto> kpis,
        List<LoadBucketDto> loadBuckets,
        List<LineOpeningSuggestionDto> lineOpeningSuggestions
) {
}
