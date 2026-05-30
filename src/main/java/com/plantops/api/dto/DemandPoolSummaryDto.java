package com.plantops.api.dto;

import java.util.List;

public record DemandPoolSummaryDto(
        List<DemandPoolKpiDto> kpis
) {
}
