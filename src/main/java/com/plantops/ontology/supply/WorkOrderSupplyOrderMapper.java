package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.WorkOrderService;

import java.math.BigDecimal;

public final class WorkOrderSupplyOrderMapper {

    private WorkOrderSupplyOrderMapper() {
    }

    public static SupplyOrder toSupplyOrder(WorkOrderEntity wo) {
        if (wo == null) {
            return null;
        }
        String productCode = wo.productCode;
        return new SupplyOrder(
                wo.workOrderNo,
                productCode,
                OntologyIds.pispId(productCode),
                quantityValue(wo.quantity),
                wo.needDate,
                mapStatus(wo.dispatchStatus),
                mapType(wo.sourceType));
    }

    private static double quantityValue(BigDecimal quantity) {
        return quantity != null ? quantity.doubleValue() : 0.0;
    }

    private static SupplyOrderStatus mapStatus(String dispatchStatus) {
        if (WorkOrderService.DISPATCH_DISPATCHED.equals(dispatchStatus)) {
            return SupplyOrderStatus.IN_PROGRESS;
        }
        return SupplyOrderStatus.OPEN;
    }

    private static SupplyOrderType mapType(String sourceType) {
        if (WorkOrderEntity.SOURCE_MANUAL.equals(sourceType)) {
            return SupplyOrderType.MANUAL_PRODUCTION;
        }
        return SupplyOrderType.PLANNED_PRODUCTION;
    }
}
