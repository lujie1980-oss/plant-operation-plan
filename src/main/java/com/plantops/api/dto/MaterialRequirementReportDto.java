package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

public record MaterialRequirementReportDto(
        List<DemandPoolKpiDto> kpis,
        LocalDate horizonStart,
        LocalDate horizonEnd,
        List<LocalDate> dates,
        List<MaterialBalanceRowDto> materials,
        List<KittingResultDto> kittingResults) {
}
