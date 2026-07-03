package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.supply.Supply;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link com.plantops.scenario.FulfillmentPeggingService#pegDemand} 规则固化进本体图：
 * 库存优先 → 工单供应 → 缺口。
 */
@ApplicationScoped
public class FulfillmentLoader {

    public void load(OntologyGraph.Builder builder) {
        Map<String, Double> inventoryAvailable = loadInventoryAvailability();
        ensureInventoryAndShortageSupplies(builder, inventoryAvailable.keySet());

        List<Demand> demands = builder.demandsById().values().stream()
                .sorted(Comparator
                        .comparingInt(FulfillmentLoader::demandPegOrder)
                        .thenComparing(Demand::getId))
                .toList();

        for (Demand demand : demands) {
            pegDemand(builder, demand, inventoryAvailable, pegContextFor(demand, builder));
        }
    }

    private static int demandPegOrder(Demand demand) {
        return switch (demand.getSourceType()) {
            case CUSTOMER_DELIVERY -> 0;
            case FORECAST -> 1;
            case BOM_COMPONENT -> 2;
        };
    }

    private static PegContext pegContextFor(Demand demand, OntologyGraph.Builder builder) {
        return switch (demand.getSourceType()) {
            case CUSTOMER_DELIVERY -> customerDeliveryContext(demand, builder);
            case BOM_COMPONENT -> new PegContext(null, 0, demand.getSourceId());
            case FORECAST -> new PegContext(null, 0, null);
        };
    }

    private static PegContext customerDeliveryContext(Demand demand, OntologyGraph.Builder builder) {
        CustomerOrderLineDelivery delivery = builder.customerOrderLineDeliveriesById().values().stream()
                .filter(d -> d.getId().equals(demand.getSourceId()))
                .findFirst()
                .orElse(null);
        if (delivery == null) {
            return new PegContext(null, 0, null);
        }
        CustomerOrderLine line = builder.customerOrderLinesById().get(delivery.getCustomerOrderLineId());
        if (line == null) {
            return new PegContext(null, 0, null);
        }
        return new PegContext(line.getSalesOrderNo(), line.getSalesOrderLineNo(), null);
    }

    private static Map<String, Double> loadInventoryAvailability() {
        Map<String, Double> available = new LinkedHashMap<>();
        for (InventoryEntity row : InventoryEntity.listInWorkspace()) {
            if (row.productCode == null || row.productCode.isBlank()) {
                continue;
            }
            double qty = row.availableQty().doubleValue();
            if (qty <= 0) {
                continue;
            }
            available.merge(row.productCode, qty, Double::sum);
        }
        return available;
    }

    private static void ensureInventoryAndShortageSupplies(
            OntologyGraph.Builder builder, Iterable<String> productCodes) {
        for (String productCode : productCodes) {
            String invId = OntologyIds.inventorySupplyId(productCode);
            if (!builder.suppliesById().containsKey(invId)) {
                builder.supply(new Supply(
                        invId,
                        productCode,
                        OntologyIds.pispId(productCode),
                        0.0,
                        null));
            }
            String shortId = OntologyIds.shortageSupplyId(productCode);
            if (!builder.suppliesById().containsKey(shortId)) {
                builder.supply(new Supply(
                        shortId,
                        productCode,
                        OntologyIds.pispId(productCode),
                        0.0,
                        null));
            }
        }
    }

    private void pegDemand(
            OntologyGraph.Builder builder,
            Demand demand,
            Map<String, Double> inventoryAvailable,
            PegContext context) {
        double remaining = demand.getQuantity();
        if (remaining <= 0) {
            return;
        }

        String productCode = demand.getProductCode();
        ensureInventoryAndShortageSupplies(builder, List.of(productCode));

        Double available = inventoryAvailable.get(productCode);
        if (available != null && available > 0) {
            double pegQty = Math.min(available, remaining);
            String supplyId = OntologyIds.inventorySupplyId(productCode);
            addFulfillment(builder, demand.getId(), supplyId, pegQty, FulfillmentType.INVENTORY_PEG);
            inventoryAvailable.put(productCode, available - pegQty);
            remaining -= pegQty;
        }

        if (remaining <= 0) {
            return;
        }

        WorkOrderEntity wo = resolveWorkOrder(
                productCode,
                context.salesOrderNo(),
                context.salesOrderLineNo(),
                context.parentWorkOrderNo());
        if (wo != null) {
            String supplyId = OntologyIds.supplyId(wo.workOrderNo, 0);
            if (builder.suppliesById().containsKey(supplyId)) {
                addFulfillment(builder, demand.getId(), supplyId, remaining, FulfillmentType.WORK_ORDER_PEG);
                return;
            }
        }

        addFulfillment(
                builder,
                demand.getId(),
                OntologyIds.shortageSupplyId(productCode),
                remaining,
                FulfillmentType.SHORTAGE_PEG);
    }

    /**
     * 与 {@link com.plantops.scenario.FulfillmentPeggingService#resolveWorkOrder} 对齐。
     */
    static WorkOrderEntity resolveWorkOrder(
            String productCode,
            String salesOrderNo,
            int salesOrderLineNo,
            String parentWorkOrderNo) {
        if (parentWorkOrderNo != null) {
            WorkOrderEntity byDep = WorkOrderEntity.findChildByDependency(parentWorkOrderNo, productCode);
            if (byDep != null) {
                return byDep;
            }
            for (WorkOrderEntity child : WorkOrderEntity.findChildren(parentWorkOrderNo)) {
                if (productCode.equals(child.productCode)) {
                    return child;
                }
            }
            return null;
        }

        if (salesOrderNo != null && !salesOrderNo.isBlank()) {
            List<WorkOrderEntity> pegged = WorkOrderEntity.findByPeggingOrderLine(
                    salesOrderNo, salesOrderLineNo, productCode);
            if (!pegged.isEmpty()) {
                return pegged.getFirst();
            }
            return WorkOrderEntity.findRootForOrderLine(salesOrderNo, salesOrderLineNo, productCode);
        }
        return null;
    }

    private static void addFulfillment(
            OntologyGraph.Builder builder,
            String demandId,
            String supplyId,
            double quantity,
            FulfillmentType type) {
        builder.fulfillment(new Fulfillment(
                OntologyIds.fulfillmentId(demandId, supplyId, type),
                demandId,
                supplyId,
                quantity,
                type));
    }

    private record PegContext(String salesOrderNo, int salesOrderLineNo, String parentWorkOrderNo) {
    }
}
