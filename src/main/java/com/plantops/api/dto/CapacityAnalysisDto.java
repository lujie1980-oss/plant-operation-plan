package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

public record CapacityAnalysisDto(
        List<DemandPoolKpiDto> kpis,
        List<LoadBucketDto> loadBuckets,
        List<LineOpeningSuggestionDto> lineOpeningSuggestions,
        LocalDate horizonStart,
        LocalDate horizonEnd
) {
}
