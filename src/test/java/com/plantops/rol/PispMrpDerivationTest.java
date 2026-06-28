package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PispMrpDerivationTest {

    private static final String FG_CODE = "FG-MRP-ROL";
    private static final String RM_CODE = "RM-MRP-ROL";
    private static final String WO_NO = "WO-MRP-ROL";

    @Test
    void changingParentSupplyRecalculatesComponentDemandViaBom() {
        LocalDate day = LocalDate.of(2026, 6, 10);
        OntologyGraph graph = buildFgRmGraph(day, 25.0, 50.0);
        RolEngine engine = RolEngine.withMasterPlanRules(graph);

        ProductInStockingPointPeriod fgPispp = pispp(graph, FG_CODE, "P-0");
        ProductInStockingPointPeriod rmPispp = pispp(graph, RM_CODE, "P-0");

        engine.applyPropertyChange(fgPispp, "plannedSupplyTotalMrp", 40.0);

        assertEquals(80.0, rmPispp.getPlannedDemandQuantityTotal(), 1e-6);
    }

    @Test
    void parentSupplyRollsComponentInventoryAfterBomDemandUpdate() {
        LocalDate day = LocalDate.of(2026, 6, 10);
        OntologyGraph graph = buildTwoPeriodFgRmGraph(day);
        RolEngine engine = RolEngine.withMasterPlanRules(graph);

        ProductInStockingPointPeriod fgP0 = pispp(graph, FG_CODE, "P-0");
        engine.applyPropertyChange(fgP0, "plannedSupplyTotalMrp", 30.0);

        ProductInStockingPointPeriod rmP0 = pispp(graph, RM_CODE, "P-0");
        ProductInStockingPointPeriod rmP1 = pispp(graph, RM_CODE, "P-1");
        assertEquals(60.0, rmP0.getPlannedDemandQuantityTotal(), 1e-6);
        assertEquals(rmP0.getPlannedInventoryLevel(), rmP1.getOnHand(), 1e-6);
    }

    private static OntologyGraph buildFgRmGraph(LocalDate day, double fgSupply, double rmDemand) {
        Period period = new Period("P-0", 0, day, day);
        String fgPispId = OntologyIds.pispId(FG_CODE);
        String rmPispId = OntologyIds.pispId(RM_CODE);

        ProductInStockingPointPeriod fgPispp = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(fgPispId, 0), fgPispId, period.getId());
        fgPispp.setPlannedSupplyTotalMrp(fgSupply);
        fgPispp.setPlannedSupplyTotal(fgSupply);
        fgPispp.recalculatePlanningFields();

        ProductInStockingPointPeriod rmPispp = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(rmPispId, 0), rmPispId, period.getId());
        rmPispp.setPlannedDemandQuantityTotal(rmDemand);
        rmPispp.recalculatePlanningFields();

        SupplyOrder supplyOrder = new SupplyOrder(
                WO_NO,
                FG_CODE,
                fgPispId,
                25.0,
                day,
                SupplyOrderStatus.OPEN,
                SupplyOrderType.PLANNED_PRODUCTION);

        Demand bomDemand = new Demand(
                OntologyIds.demandFromBomId(WO_NO, RM_CODE),
                RM_CODE,
                rmPispId,
                50.0,
                day,
                5,
                DemandSourceType.BOM_COMPONENT,
                WO_NO);

        return OntologyGraph.builder()
                .periodsOrdered(List.of(period))
                .pisp(new ProductInStockingPoint(fgPispId, FG_CODE, OntologyIds.DEFAULT_FG, FG_CODE))
                .pisp(new ProductInStockingPoint(rmPispId, RM_CODE, OntologyIds.DEFAULT_FG, RM_CODE))
                .pispPeriod(fgPispp)
                .pispPeriod(rmPispp)
                .supplyOrder(supplyOrder)
                .demand(bomDemand)
                .build();
    }

    private static OntologyGraph buildTwoPeriodFgRmGraph(LocalDate start) {
        Period p0 = new Period("P-0", 0, start, start);
        Period p1 = new Period("P-1", 1, start.plusDays(1), start.plusDays(1));
        String fgPispId = OntologyIds.pispId(FG_CODE);
        String rmPispId = OntologyIds.pispId(RM_CODE);

        ProductInStockingPointPeriod fg0 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(fgPispId, 0), fgPispId, p0.getId());
        fg0.setPlannedSupplyTotalMrp(10);
        fg0.setPlannedSupplyTotal(10);
        fg0.recalculatePlanningFields();

        ProductInStockingPointPeriod fg1 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(fgPispId, 1), fgPispId, p1.getId());
        fg1.recalculatePlanningFields();

        ProductInStockingPointPeriod rm0 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(rmPispId, 0), rmPispId, p0.getId());
        rm0.setPlannedDemandQuantityTotal(20);
        rm0.recalculatePlanningFields();

        ProductInStockingPointPeriod rm1 = new ProductInStockingPointPeriod(
                OntologyIds.pisppId(rmPispId, 1), rmPispId, p1.getId());
        rm1.setOnHand(0);
        rm1.recalculatePlanningFields();

        PispRolling.rollChain(List.of(fg0, fg1));
        PispRolling.rollChain(List.of(rm0, rm1));

        SupplyOrder supplyOrder = new SupplyOrder(
                WO_NO,
                FG_CODE,
                fgPispId,
                10.0,
                start,
                SupplyOrderStatus.OPEN,
                SupplyOrderType.PLANNED_PRODUCTION);

        Demand bomDemand = new Demand(
                OntologyIds.demandFromBomId(WO_NO, RM_CODE),
                RM_CODE,
                rmPispId,
                20.0,
                start,
                5,
                DemandSourceType.BOM_COMPONENT,
                WO_NO);

        return OntologyGraph.builder()
                .periodsOrdered(List.of(p0, p1))
                .pisp(new ProductInStockingPoint(fgPispId, FG_CODE, OntologyIds.DEFAULT_FG, FG_CODE))
                .pisp(new ProductInStockingPoint(rmPispId, RM_CODE, OntologyIds.DEFAULT_FG, RM_CODE))
                .pispPeriod(fg0)
                .pispPeriod(fg1)
                .pispPeriod(rm0)
                .pispPeriod(rm1)
                .supplyOrder(supplyOrder)
                .demand(bomDemand)
                .build();
    }

    private static ProductInStockingPointPeriod pispp(OntologyGraph graph, String productCode, String periodId) {
        String pispId = OntologyIds.pispId(productCode);
        return graph.pispPeriodsById().values().stream()
                .filter(p -> pispId.equals(p.getPispId()) && periodId.equals(p.getPeriodId()))
                .findFirst()
                .orElseThrow();
    }
}
