package com.plantops.ontology.material;

import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.KittingResultDto;
import com.plantops.api.dto.MaterialBalanceDayDto;
import com.plantops.api.dto.MaterialBalanceRowDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.scheduling.PispDailyClosingProjection;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.scenario.RuleScopeHelper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 从 {@link OntologyGraph} 的 PISPP 链投影物料平衡表（期初 / 需求 / 供应 / 期末 / 缺口）。
 */
@ApplicationScoped
public class OntologyMaterialBalanceProjector {

    @Inject
    RuleScopeHelper ruleScopeHelper;

    public MaterialRequirementReportDto project(OntologyGraph graph) {
        List<Period> periods = graph.periodsOrdered();
        if (periods.isEmpty()) {
            LocalDate today = LocalDate.now();
            return emptyReport(today, today);
        }
        LocalDate horizonStart = periods.get(0).getStartDate();
        LocalDate horizonEnd = periods.get(periods.size() - 1).getEndDate();
        if (horizonStart == null || horizonEnd == null) {
            LocalDate today = LocalDate.now();
            return emptyReport(today, today.plusDays(14));
        }
        List<LocalDate> dates = dateRange(horizonStart, horizonEnd);
        Set<String> criticalMaterials = loadCriticalMaterials();

        List<MaterialBalanceRowDto> rows = new ArrayList<>();
        BigDecimal totalShortage = BigDecimal.ZERO;
        int materialsWithGap = 0;

        List<String> productCodes = graph.pispsById().values().stream()
                .map(ProductInStockingPoint::getProductCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .distinct()
                .toList();

        for (String productCode : productCodes) {
            String pispId = graph.pispsById().values().stream()
                    .filter(p -> productCode.equals(p.getProductCode()))
                    .map(ProductInStockingPoint::getId)
                    .findFirst()
                    .orElse(null);
            if (pispId == null) {
                continue;
            }
            List<ProductInStockingPointPeriod> chain =
                    PispDailyClosingProjection.chainForPisp(graph, pispId, periods);
            if (chain.isEmpty()) {
                continue;
            }

            Map<LocalDate, DailyMetrics> metricsByDay = projectDailyMetrics(chain, periods);
            BigDecimal carry = BigDecimal.valueOf(chain.get(0).getOnHand());
            List<MaterialBalanceDayDto> days = new ArrayList<>();
            BigDecimal rowShortage = BigDecimal.ZERO;

            for (LocalDate day : dates) {
                DailyMetrics metrics = metricsByDay.get(day);
                if (metrics == null) {
                    days.add(new MaterialBalanceDayDto(day, carry, BigDecimal.ZERO, BigDecimal.ZERO, carry, BigDecimal.ZERO));
                    continue;
                }
                days.add(new MaterialBalanceDayDto(
                        day,
                        metrics.opening(),
                        metrics.demand(),
                        metrics.supply(),
                        metrics.closing(),
                        metrics.shortage()));
                carry = metrics.closing();
                rowShortage = rowShortage.add(metrics.shortage());
            }

            if (rowShortage.compareTo(BigDecimal.ZERO) > 0) {
                materialsWithGap++;
            }
            totalShortage = totalShortage.add(rowShortage);
            rows.add(new MaterialBalanceRowDto(
                    productCode,
                    criticalMaterials.contains(productCode),
                    rowShortage,
                    days));
        }

        rows.sort(Comparator
                .comparing(MaterialBalanceRowDto::totalShortageQty)
                .reversed()
                .thenComparing(MaterialBalanceRowDto::productCode));

        List<DemandPoolKpiDto> kpis = buildKpis(rows.size(), materialsWithGap, totalShortage);
        return new MaterialRequirementReportDto(
                kpis,
                horizonStart,
                horizonEnd,
                dates,
                rows,
                List.of());
    }

    private Map<LocalDate, DailyMetrics> projectDailyMetrics(
            List<ProductInStockingPointPeriod> chain,
            List<Period> periods) {
        Map<String, Period> periodById = new LinkedHashMap<>();
        for (Period period : periods) {
            periodById.put(period.getId(), period);
        }
        TreeMap<LocalDate, DailyMetrics> metricsByDay = new TreeMap<>();
        BigDecimal carry = BigDecimal.valueOf(chain.get(0).getOnHand());

        for (ProductInStockingPointPeriod pispp : chain) {
            Period period = periodById.get(pispp.getPeriodId());
            if (period == null || period.getStartDate() == null || period.getEndDate() == null) {
                continue;
            }
            List<LocalDate> days = dateRange(period.getStartDate(), period.getEndDate());
            if (days.isEmpty()) {
                continue;
            }
            BigDecimal divisor = BigDecimal.valueOf(days.size());
            BigDecimal dailySupply = BigDecimal.valueOf(pispp.getPlannedSupplyTotal())
                    .divide(divisor, 10, RoundingMode.HALF_UP);
            BigDecimal dailyDemand = BigDecimal.valueOf(pispp.getPlannedDemandQuantityTotal())
                    .divide(divisor, 10, RoundingMode.HALF_UP);

            for (LocalDate day : days) {
                BigDecimal opening = carry;
                BigDecimal closingRaw = opening.add(dailySupply).subtract(dailyDemand);
                BigDecimal shortage = closingRaw.compareTo(BigDecimal.ZERO) < 0
                        ? closingRaw.negate()
                        : BigDecimal.ZERO;
                BigDecimal closing = closingRaw.max(BigDecimal.ZERO);
                carry = closing;
                metricsByDay.put(day, new DailyMetrics(opening, dailyDemand, dailySupply, closing, shortage));
            }
        }
        return metricsByDay;
    }

    private Set<String> loadCriticalMaterials() {
        Set<String> critical = new HashSet<>();
        for (BomComponentEntity row : BomComponentEntity.listInWorkspace()) {
            if (ruleScopeHelper.criticalForMasterPlan(row)) {
                critical.add(row.componentProductCode);
            }
        }
        return critical;
    }

    private static List<DemandPoolKpiDto> buildKpis(
            int materialCount,
            int materialsWithGap,
            BigDecimal totalShortage) {
        double coverage = materialCount == 0
                ? 100
                : (double) (materialCount - materialsWithGap) / materialCount * 100;
        List<DemandPoolKpiDto> kpis = new ArrayList<>();
        kpis.add(new DemandPoolKpiDto("mrp_material_count", "物料种类", materialCount, "种", "info"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_gap_materials",
                "缺料物料",
                materialsWithGap,
                "种",
                materialsWithGap > 0 ? "danger" : "ok"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_total_shortage",
                "缺料总量",
                totalShortage.setScale(0, RoundingMode.HALF_UP).doubleValue(),
                "件",
                totalShortage.compareTo(BigDecimal.ZERO) > 0 ? "warn" : "ok"));
        kpis.add(new DemandPoolKpiDto(
                "mrp_coverage",
                "覆盖率",
                Math.round(coverage * 10) / 10.0,
                "%",
                coverage >= 90 ? "ok" : coverage >= 70 ? "warn" : "danger"));
        kpis.add(new DemandPoolKpiDto("mrp_ontology_mode", "数据源", 1, "PISPP", "info"));
        return kpis;
    }

    private static MaterialRequirementReportDto emptyReport(LocalDate start, LocalDate end) {
        return new MaterialRequirementReportDto(
                List.of(new DemandPoolKpiDto("mrp_material_count", "物料种类", 0, "种", "info")),
                start,
                end,
                dateRange(start, end),
                List.of(),
                List.<KittingResultDto>of());
    }

    private static List<LocalDate> dateRange(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private record DailyMetrics(
            BigDecimal opening,
            BigDecimal demand,
            BigDecimal supply,
            BigDecimal closing,
            BigDecimal shortage) {
    }
}
