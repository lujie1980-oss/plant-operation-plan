package com.plantops.rol;

import com.plantops.ontology.period.ProductInStockingPointPeriod;

import java.util.List;

public final class PispRolling {

    private PispRolling() {
    }

    public static void rollChain(List<ProductInStockingPointPeriod> ordered) {
        if (ordered == null || ordered.size() < 2) {
            return;
        }
        for (int i = 1; i < ordered.size(); i++) {
            ProductInStockingPointPeriod previous = ordered.get(i - 1);
            ProductInStockingPointPeriod current = ordered.get(i);
            previous.recalculatePlanningFields();
            current.setOnHand(previous.getPlannedInventoryLevel());
            current.recalculatePlanningFields();
        }
    }
}
