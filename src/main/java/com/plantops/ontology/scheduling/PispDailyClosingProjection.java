package com.plantops.ontology.scheduling;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 将 PISPP 周期桶投影为按日期末可用量序列，供 {@link com.plantops.scenario.planning.MaterialFeasibilitySnapshotBuilder} 使用。
 * <p>
 * 策略：每个 period 内将 {@code plannedSupplyTotal} / {@code plannedDemandQuantityTotal} 均匀摊到各日历日，
 * 再按日滚动闭合（负库存截断为 0），与 {@link com.plantops.scenario.MaterialFeasibilityService} 的日序列形状对齐。
 */
public final class PispDailyClosingProjection {

    private PispDailyClosingProjection() {
    }

    public static Map<String, NavigableMap<LocalDate, BigDecimal>> projectGraph(OntologyGraph graph) {
        if (graph == null || graph.pispsById().isEmpty()) {
            return Map.of();
        }
        Map<String, NavigableMap<LocalDate, BigDecimal>> closingByProduct = new LinkedHashMap<>();
        List<Period> periods = graph.periodsOrdered();
        for (ProductInStockingPoint pisp : graph.pispsById().values()) {
            if (pisp.getProductCode() == null || pisp.getProductCode().isBlank()) {
                continue;
            }
            List<ProductInStockingPointPeriod> chain = chainForPisp(graph, pisp.getId(), periods);
            if (chain.isEmpty()) {
                continue;
            }
            closingByProduct.put(pisp.getProductCode(), projectChain(chain, periods));
        }
        return closingByProduct;
    }

    public static NavigableMap<LocalDate, BigDecimal> projectChain(
            List<ProductInStockingPointPeriod> orderedChain,
            List<Period> periodsOrdered) {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        if (orderedChain == null || orderedChain.isEmpty()) {
            return series;
        }
        Map<String, Period> periodById = indexPeriods(periodsOrdered);
        BigDecimal carry = BigDecimal.valueOf(orderedChain.get(0).getOnHand());

        for (ProductInStockingPointPeriod pispp : orderedChain) {
            Period period = periodById.get(pispp.getPeriodId());
            if (period == null || period.getStartDate() == null || period.getEndDate() == null) {
                continue;
            }
            List<LocalDate> days = dateRange(period.getStartDate(), period.getEndDate());
            if (days.isEmpty()) {
                continue;
            }
            BigDecimal supply = BigDecimal.valueOf(pispp.getPlannedSupplyTotal());
            BigDecimal demand = BigDecimal.valueOf(pispp.getPlannedDemandQuantityTotal());
            BigDecimal divisor = BigDecimal.valueOf(days.size());
            BigDecimal dailySupply = supply.divide(divisor, 10, RoundingMode.HALF_UP);
            BigDecimal dailyDemand = demand.divide(divisor, 10, RoundingMode.HALF_UP);

            for (LocalDate day : days) {
                carry = carry.add(dailySupply).subtract(dailyDemand);
                if (carry.compareTo(BigDecimal.ZERO) < 0) {
                    carry = BigDecimal.ZERO;
                }
                series.put(day, carry);
            }
        }
        return series;
    }

    public static List<ProductInStockingPointPeriod> chainForPisp(
            OntologyGraph graph,
            String pispId,
            List<Period> periodsOrdered) {
        Map<String, Integer> sequenceByPeriodId = new HashMap<>();
        for (Period period : periodsOrdered) {
            sequenceByPeriodId.put(period.getId(), period.getSequenceNr());
        }
        return graph.pispPeriodsById().values().stream()
                .filter(pispp -> pispId.equals(pispp.getPispId()))
                .sorted(Comparator.comparingInt(pispp ->
                        sequenceByPeriodId.getOrDefault(pispp.getPeriodId(), Integer.MAX_VALUE)))
                .toList();
    }

    private static Map<String, Period> indexPeriods(List<Period> periodsOrdered) {
        Map<String, Period> periodById = new LinkedHashMap<>();
        if (periodsOrdered != null) {
            for (Period period : periodsOrdered) {
                periodById.put(period.getId(), period);
            }
        }
        return periodById;
    }

    private static List<LocalDate> dateRange(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        if (start == null || end == null) {
            return days;
        }
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            days.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return days;
    }
}
