package com.plantops.ontology.period;

import com.plantops.rol.PispRolling;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductInStockingPointPeriodTest {

    @Test
    void plannedInventoryLevelFromOnHandSupplyDemand() {
        var p = new ProductInStockingPointPeriod("PISPP-1", "PISP-FG-100", "P-0");
        p.setOnHand(100);
        p.setPlannedSupplyTotal(50);
        p.setPlannedDemandQuantityTotal(30);
        p.recalculatePlanningFields();
        assertEquals(120, p.getPlannedInventoryLevel(), 1e-6);
        assertEquals(150, p.getReplenishedInventoryLevel(), 1e-6);
    }

    @Test
    void stockShortageWhenBelowTarget() {
        var p = new ProductInStockingPointPeriod("PISPP-1", "PISP-FG-100", "P-0");
        p.setOnHand(10);
        p.setPlannedSupplyTotal(0);
        p.setPlannedDemandQuantityTotal(50);
        p.setInventoryTargetQuantity(20);
        p.recalculatePlanningFields();
        assertEquals(60, p.getStockShortageQuantity(), 1e-6);
    }

    @Test
    void mrpAndOptimizedSupplyTrackedSeparately() {
        var p = new ProductInStockingPointPeriod("PISPP-1", "PISP-FG-100", "P-0");
        p.setPlannedSupplyTotalMrp(100);
        p.setPlannedSupplyTotalOptimized(80);
        p.setPlannedSupplyTotal(80);
        p.setPlannedDemandQuantityTotal(50);
        p.recalculatePlanningFields();
        assertEquals(100, p.getPlannedSupplyTotalMrp(), 1e-6);
        assertEquals(80, p.getPlannedSupplyTotalOptimized(), 1e-6);
        assertEquals(30, p.getPlannedInventoryLevel(), 1e-6);
    }

    @Test
    void rollOnHandFromPreviousPlannedLevel() {
        var p0 = new ProductInStockingPointPeriod("PP-0", "PISP-1", "P-0");
        p0.setOnHand(100);
        p0.setPlannedSupplyTotal(40);
        p0.setPlannedDemandQuantityTotal(30);
        p0.recalculatePlanningFields();

        var p1 = new ProductInStockingPointPeriod("PP-1", "PISP-1", "P-1");
        PispRolling.rollChain(List.of(p0, p1));
        assertEquals(110, p1.getOnHand(), 1e-6);
        p1.setPlannedSupplyTotal(0);
        p1.setPlannedDemandQuantityTotal(20);
        p1.recalculatePlanningFields();
        assertEquals(90, p1.getPlannedInventoryLevel(), 1e-6);
    }
}
