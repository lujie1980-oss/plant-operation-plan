package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.demand.ForecastDemand;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.OperationOutputMaterial;
import com.plantops.ontology.supply.PlanUnit;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ForecastDemandEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SupplyChainLoader {

    @Inject
    FulfillmentLoader fulfillmentLoader;

    @Inject
    BomDependencyDerivation bomDependencyDerivation;

    @Inject
    com.plantops.transactional.internal.TxnOntologyLoadContributor txnOntologyLoadContributor;

    public void expand(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        expandDemandsAndStructureOnly(builder, supplyOrders);
        fulfillmentLoader.load(builder);
        bomDependencyDerivation.derive(builder);
    }

    /** 装载需求与制造结构，不执行 Fulfillment 挂接（供上游满足链本体求解使用）。 */
    public void expandDemandsAndStructureOnly(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        loadIndependentDemands(builder);
        expandManufacturingChain(builder, supplyOrders);
    }

    public void runFulfillmentPegging(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        fulfillmentLoader.load(builder);
        bomDependencyDerivation.derive(builder);
    }

    /** 仅装载指定订单行交付需求（供上游满足链，避免扫全场景订单）。 */
    public void loadSingleCustomerDelivery(
            OntologyGraph.Builder builder, OntologyIds.CustomerOrderLineDeliveryKey deliveryKey) {
        if (deliveryKey == null) {
            return;
        }
        SalesOrderLineEntity line =
                SalesOrderLineEntity.findByKey(deliveryKey.salesOrderNo(), deliveryKey.salesOrderLineNo());
        if (line == null || "CANCELLED".equals(line.status) || line.productCode == null || line.productCode.isBlank()) {
            return;
        }
        String colId = OntologyIds.customerOrderLineId(line.salesOrderNo, line.salesOrderLineNo);
        double orderQty = line.orderQty != null ? line.orderQty.doubleValue() : 0.0;
        builder.customerOrderLine(new CustomerOrderLine(
                colId,
                line.salesOrderNo,
                line.salesOrderLineNo,
                line.customerCode,
                line.productCode,
                orderQty));

        String coldId = OntologyIds.customerOrderLineDeliveryId(
                line.salesOrderNo, line.salesOrderLineNo, deliveryKey.deliverySeq());
        builder.customerOrderLineDelivery(new CustomerOrderLineDelivery(
                coldId,
                colId,
                orderQty,
                line.promiseDate,
                line.dueDate,
                line.status));

        builder.demand(new Demand(
                OntologyIds.demandFromCustomerDeliveryId(coldId),
                line.productCode,
                OntologyIds.pispId(line.productCode),
                orderQty,
                line.dueDate,
                line.priority,
                DemandSourceType.CUSTOMER_DELIVERY,
                coldId));
    }

    private void loadIndependentDemands(OntologyGraph.Builder builder) {
        if (txnOntologyLoadContributor.hasTransactionalDemands()) {
            txnOntologyLoadContributor.loadDemandsFromTxn(builder);
            loadForecastDemands(builder);
            return;
        }
        loadLegacySalesOrderDemands(builder);
        loadForecastDemands(builder);
    }

    private static void loadLegacySalesOrderDemands(OntologyGraph.Builder builder) {
        for (SalesOrderLineEntity line : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(line.status)) {
                continue;
            }
            if (line.productCode == null || line.productCode.isBlank()) {
                continue;
            }
            String colId = OntologyIds.customerOrderLineId(line.salesOrderNo, line.salesOrderLineNo);
            double orderQty = line.orderQty != null ? line.orderQty.doubleValue() : 0.0;
            builder.customerOrderLine(new CustomerOrderLine(
                    colId,
                    line.salesOrderNo,
                    line.salesOrderLineNo,
                    line.customerCode,
                    line.productCode,
                    orderQty));

            String coldId = OntologyIds.customerOrderLineDeliveryId(
                    line.salesOrderNo, line.salesOrderLineNo, 0);
            CustomerOrderLineDelivery delivery = new CustomerOrderLineDelivery(
                    coldId,
                    colId,
                    orderQty,
                    line.promiseDate,
                    line.dueDate,
                    line.status);
            builder.customerOrderLineDelivery(delivery);

            builder.demand(new Demand(
                    OntologyIds.demandFromCustomerDeliveryId(coldId),
                    line.productCode,
                    OntologyIds.pispId(line.productCode),
                    orderQty,
                    line.dueDate,
                    line.priority,
                    DemandSourceType.CUSTOMER_DELIVERY,
                    coldId));
        }
    }

    private static void loadForecastDemands(OntologyGraph.Builder builder) {
        for (ForecastDemandEntity row : ForecastDemandEntity.listInWorkspace()) {
            if (row.productCode == null || row.productCode.isBlank()) {
                continue;
            }
            String fcId = OntologyIds.forecastDemandId(row.forecastId);
            double qty = row.quantity != null ? row.quantity.doubleValue() : 0.0;
            double confidence = row.confidence != null ? row.confidence.doubleValue() : 0.8;
            builder.forecastDemand(new ForecastDemand(
                    fcId,
                    row.productCode,
                    qty,
                    row.forecastPeriod,
                    row.needDate,
                    confidence));
            builder.demand(new Demand(
                    OntologyIds.demandFromForecastId(fcId),
                    row.productCode,
                    OntologyIds.pispId(row.productCode),
                    qty,
                    row.needDate,
                    5,
                    DemandSourceType.FORECAST,
                    fcId));
        }
    }

    private void expandManufacturingChain(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        for (SupplyOrder supplyOrder : supplyOrders) {
            String planUnitId = OntologyIds.planUnitId(supplyOrder.getId(), 0);
            builder.planUnit(new PlanUnit(planUnitId, supplyOrder.getId(), supplyOrder.getQuantity(), 0));

            List<Operation> operations = builder.operationsById().values().stream()
                    .filter(op -> supplyOrder.getId().equals(op.getSupplyOrderId()))
                    .sorted(java.util.Comparator.comparingInt(Operation::getSequenceNr))
                    .toList();

            for (Operation operation : operations) {
                operation.setPlanUnitId(planUnitId);
                builder.operation(operation);
            }

            if (operations.isEmpty()) {
                continue;
            }

            Operation lastOp = operations.get(operations.size() - 1);
            String supplyId = OntologyIds.supplyId(supplyOrder.getId(), 0);
            builder.supply(new Supply(
                    supplyId,
                    supplyOrder.getProductCode(),
                    supplyOrder.getPispId(),
                    supplyOrder.getQuantity(),
                    supplyOrder.getId()));
            builder.operationOutputMaterial(new OperationOutputMaterial(
                    OntologyIds.operationOutputMaterialId(lastOp.getId(), supplyId),
                    lastOp.getId(),
                    supplyId,
                    supplyOrder.getQuantity()));

            Operation inputOp = operations.get(0);
            String finishedProduct = resolveFinishedProduct(supplyOrder);
            for (BomComponentEntity bom : BomComponentEntity.findChildren(finishedProduct, supplyOrder.getProductCode())) {
                if (!bom.isCriticalComponent) {
                    continue;
                }
                double componentQty = bom.componentQty != null
                        ? bom.componentQty.doubleValue() * supplyOrder.getQuantity()
                        : supplyOrder.getQuantity();
                String demandId = OntologyIds.demandFromBomId(supplyOrder.getId(), bom.componentProductCode);
                builder.demand(new Demand(
                        demandId,
                        bom.componentProductCode,
                        OntologyIds.pispId(bom.componentProductCode),
                        componentQty,
                        supplyOrder.getNeedDate(),
                        5,
                        DemandSourceType.BOM_COMPONENT,
                        supplyOrder.getId()));
                builder.operationInputMaterial(new OperationInputMaterial(
                        OntologyIds.operationInputMaterialId(inputOp.getId(), demandId),
                        inputOp.getId(),
                        demandId,
                        componentQty));
            }
        }
    }

    private static String resolveFinishedProduct(SupplyOrder supplyOrder) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(supplyOrder.getId());
        if (wo != null) {
            return BomComponentEntity.resolveFinishedProduct(wo);
        }
        return supplyOrder.getProductCode();
    }
}
