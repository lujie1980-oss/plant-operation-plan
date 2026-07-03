package com.plantops.ontology.scheduling;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.rol.PispRolling;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PispDailyClosingProjectionTest {

    @Test
    void spreadsPeriodSupplyAndDemandUniformlyAcrossDays() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        Period period = new Period(OntologyIds.periodId(0), 0, start, start.plusDays(1));
        String pispId = OntologyIds.pispId("FG-CLOSE-1");

        ProductInStockingPointPeriod p0 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(pispId, 0), pispId, period.getId());
        p0.setOnHand(100);
        p0.setPlannedSupplyTotal(20);
        p0.setPlannedDemandQuantityTotal(10);
        p0.recalculatePlanningFields();

        NavigableMap<LocalDate, BigDecimal> series =
                PispDailyClosingProjection.projectChain(List.of(p0), List.of(period));

        assertEquals(2, series.size());
        assertEquals(0, new BigDecimal("105").compareTo(series.get(start)));
        assertEquals(0, new BigDecimal("110").compareTo(series.get(start.plusDays(1))));
    }

    @Test
    void projectGraphBuildsSeriesPerProduct() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        String productCode = "FG-CLOSE-2";
        String pispId = OntologyIds.pispId(productCode);
        Period period = new Period(OntologyIds.periodId(0), 0, start, start);

        ProductInStockingPointPeriod pispp = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(pispId, 0), pispId, period.getId());
        pispp.setOnHand(50);
        pispp.setPlannedSupplyTotal(10);
        pispp.recalculatePlanningFields();

        OntologyGraph graph = OntologyGraph.builder()
                .pisp(new ProductInStockingPoint(pispId, productCode, OntologyIds.DEFAULT_FG, productCode))
                .periodsOrdered(List.of(period))
                .pispPeriod(pispp)
                .build();

        NavigableMap<LocalDate, BigDecimal> series =
                PispDailyClosingProjection.projectGraph(graph).get(productCode);
        assertNotNull(series);
        assertEquals(0, new BigDecimal("60").compareTo(series.get(start)));
    }

    @Test
    void rolledChainCarriesInventoryAcrossPeriods() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        String pispId = OntologyIds.pispId("FG-CLOSE-3");
        Period p0 = new Period(OntologyIds.periodId(0), 0, start, start);
        Period p1 = new Period(OntologyIds.periodId(1), 1, start.plusDays(1), start.plusDays(1));

        ProductInStockingPointPeriod chain0 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(pispId, 0), pispId, p0.getId());
        chain0.setOnHand(40);
        chain0.setPlannedSupplyTotal(10);
        chain0.setPlannedDemandQuantityTotal(5);
        chain0.recalculatePlanningFields();

        ProductInStockingPointPeriod chain1 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(pispId, 1), pispId, p1.getId());
        chain1.setPlannedSupplyTotal(0);
        chain1.setPlannedDemandQuantityTotal(20);

        List<ProductInStockingPointPeriod> chain = List.of(chain0, chain1);
        PispRolling.rollChain(chain);

        NavigableMap<LocalDate, BigDecimal> series =
                PispDailyClosingProjection.projectChain(chain, List.of(p0, p1));

        assertEquals(0, new BigDecimal("45").compareTo(series.get(start)));
        assertEquals(0, new BigDecimal("25").compareTo(series.get(start.plusDays(1))));
    }
}
