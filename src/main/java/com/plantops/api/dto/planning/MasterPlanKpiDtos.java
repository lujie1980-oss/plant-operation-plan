package com.plantops.api.dto.planning;

import java.util.List;

/** §15 KPI-MP-TOT / KPI-MP-B01~B10 REST shapes (TODO-16). */
public final class MasterPlanKpiDtos {

    private MasterPlanKpiDtos() {}

    public record KpiDomainScoreDto(
            String domain,
            int hard,
            int soft) {}

    public record KpiItemDto(
            String kpiId,
            String name,
            String constraintId,
            int hard,
            int soft) {}

    public record KpiBreakdownDto(
            KpiDomainScoreDto delivery,
            KpiDomainScoreDto material,
            KpiDomainScoreDto capacity,
            KpiDomainScoreDto supply,
            KpiDomainScoreDto preference,
            List<KpiItemDto> scoring,
            List<KpiItemDto> constraint) {}

    public record BusinessKpiDto(
            String kpiId,
            String name,
            double value,
            String unit,
            String severity) {}

    public record MasterPlanKpisResponseDto(
            String planVersionId,
            Integer totalKpi,
            String scoreSummary,
            KpiBreakdownDto kpiBreakdown,
            List<BusinessKpiDto> businessKpis) {}
}
