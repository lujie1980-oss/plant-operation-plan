package com.plantops.transactional.internal;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.ontology.demand.CustomerOrderLineDelivery;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationResourceBinding;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;
import com.plantops.persistence.entity.TxnCustomerOrderLineDeliveryEntity;
import com.plantops.persistence.entity.TxnCustomerOrderLineEntity;
import com.plantops.persistence.entity.TxnDemandEntity;
import com.plantops.persistence.entity.TxnOperationEntity;
import com.plantops.persistence.entity.TxnOperationOsrEntity;
import com.plantops.persistence.entity.TxnSupplyOrderEntity;
import com.plantops.scenario.WorkOrderService;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** §12 · TODO-14 T4：从 txn_* 装载 OG 需求/供应（AC-TX-04 · RULE-TX-01）。 */
@ApplicationScoped
public class TxnOntologyLoadContributor {

    public boolean hasTransactionalDemands() {
        return !TxnDemandEntity.listInWorkspace().isEmpty();
    }

    public boolean hasTransactionalSupplyOrders() {
        return TxnSupplyOrderEntity.listInWorkspace().stream()
                .anyMatch(so -> TxnSupplyOrderEntity.FIRM_STATUS_FIRM.equals(so.firmStatus));
    }

    public void loadDemandsFromTxn(OntologyGraph.Builder builder) {
        Map<String, TxnCustomerOrderLineEntity> colsByKey = TxnCustomerOrderLineEntity.listInWorkspace().stream()
                .collect(Collectors.toMap(
                        col -> col.customerOrderNo + "|" + col.lineNo, col -> col, (a, b) -> a));

        for (TxnCustomerOrderLineDeliveryEntity cold : TxnCustomerOrderLineDeliveryEntity.listInWorkspace()) {
            addCustomerDeliveryFromTxn(builder, cold, colsByKey);
        }
    }

    public void loadSingleCustomerDelivery(
            OntologyGraph.Builder builder, OntologyIds.CustomerOrderLineDeliveryKey deliveryKey) {
        if (deliveryKey == null) {
            return;
        }
        TxnCustomerOrderLineDeliveryEntity cold = TxnCustomerOrderLineDeliveryEntity.find(
                        "workspaceId = ?1 and customerOrderNo = ?2 and lineNo = ?3 and deliverySeq = ?4",
                        TxnCustomerOrderLineDeliveryEntity.ws(),
                        deliveryKey.salesOrderNo(),
                        deliveryKey.salesOrderLineNo(),
                        deliveryKey.deliverySeq())
                .firstResult();
        if (cold == null) {
            return;
        }
        Map<String, TxnCustomerOrderLineEntity> colsByKey = TxnCustomerOrderLineEntity.listInWorkspace().stream()
                .collect(Collectors.toMap(
                        col -> col.customerOrderNo + "|" + col.lineNo, col -> col, (a, b) -> a));
        addCustomerDeliveryFromTxn(builder, cold, colsByKey);
    }

    private static void addCustomerDeliveryFromTxn(
            OntologyGraph.Builder builder,
            TxnCustomerOrderLineDeliveryEntity cold,
            Map<String, TxnCustomerOrderLineEntity> colsByKey) {
        if ("CANCELLED".equals(cold.status)) {
            return;
        }
        TxnCustomerOrderLineEntity col = colsByKey.get(cold.customerOrderNo + "|" + cold.lineNo);
        if (col == null || col.productCode == null || col.productCode.isBlank()) {
            return;
        }
        String colId = OntologyIds.customerOrderLineId(cold.customerOrderNo, cold.lineNo);
        double orderQty = col.orderQty != null ? col.orderQty.doubleValue() : 0.0;
        builder.customerOrderLine(new CustomerOrderLine(
                colId, cold.customerOrderNo, cold.lineNo, null, col.productCode, orderQty));

        String coldId = OntologyIds.customerOrderLineDeliveryId(
                cold.customerOrderNo, cold.lineNo, cold.deliverySeq);
        double deliveryQty = cold.deliveryQty != null ? cold.deliveryQty.doubleValue() : orderQty;
        builder.customerOrderLineDelivery(new CustomerOrderLineDelivery(
                coldId, colId, deliveryQty, cold.confirmedDate, cold.requestedDate, cold.status));

        TxnDemandEntity txnDemand = TxnDemandEntity.find(
                        "workspaceId = ?1 and sourceId = ?2",
                        TxnDemandEntity.ws(),
                        coldId)
                .firstResult();
        if (txnDemand != null) {
            builder.demand(new Demand(
                    txnDemand.demandId,
                    txnDemand.productCode,
                    OntologyIds.pispId(txnDemand.productCode),
                    txnDemand.quantity != null ? txnDemand.quantity.doubleValue() : deliveryQty,
                    txnDemand.needDate,
                    txnDemand.priority != null ? txnDemand.priority : 5,
                    DemandSourceType.CUSTOMER_DELIVERY,
                    coldId));
        }
    }

    public List<SupplyOrder> loadFirmSupplyOrdersFromTxn(OntologyGraph.Builder builder) {
        List<SupplyOrder> supplyOrders = new ArrayList<>();
        for (TxnSupplyOrderEntity so : TxnSupplyOrderEntity.listInWorkspace()) {
            if (!TxnSupplyOrderEntity.FIRM_STATUS_FIRM.equals(so.firmStatus)) {
                continue;
            }
            if (so.dispatchStatus != null && WorkOrderService.DISPATCH_DISPATCHED.equals(so.dispatchStatus)) {
                continue;
            }
            SupplyOrder supplyOrder = new SupplyOrder(
                    so.supplyOrderId,
                    so.productCode,
                    OntologyIds.pispId(so.productCode),
                    so.quantity != null ? so.quantity.doubleValue() : 0.0,
                    so.needDate != null ? so.needDate : java.time.LocalDate.now(),
                    SupplyOrderStatus.OPEN,
                    SupplyOrderType.MANUAL_PRODUCTION);
            supplyOrders.add(supplyOrder);
            builder.supplyOrder(supplyOrder);
        }
        return supplyOrders;
    }

    public void loadOperationsFromTxn(OntologyGraph.Builder builder, List<SupplyOrder> supplyOrders) {
        Map<String, List<TxnOperationEntity>> opsBySo = TxnOperationEntity.listInWorkspace().stream()
                .collect(Collectors.groupingBy(op -> op.supplyOrderId));
        Map<String, List<TxnOperationOsrEntity>> osrByOp = TxnOperationOsrEntity.listInWorkspace().stream()
                .collect(Collectors.groupingBy(osr -> osr.operationId));

        for (SupplyOrder supplyOrder : supplyOrders) {
            List<TxnOperationEntity> ops = opsBySo.getOrDefault(supplyOrder.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(op -> op.routingSequenceNo))
                    .toList();
            if (ops.isEmpty()) {
                continue;
            }
            for (int i = 0; i < ops.size(); i++) {
                TxnOperationEntity txnOp = ops.get(i);
                Operation operation = new Operation(
                        txnOp.operationId,
                        supplyOrder.getId(),
                        i,
                        txnOp.operationName != null ? txnOp.operationName : "OP-" + txnOp.routingSequenceNo);
                operation.setRoutingSequenceNo(txnOp.routingSequenceNo);
                operation.setPlanUnitId(txnOp.planUnitId);
                builder.operation(operation);

                List<TxnOperationOsrEntity> osrs = osrByOp.getOrDefault(txnOp.operationId, List.of()).stream()
                        .sorted(Comparator.comparingInt(o -> o.resourcePriority))
                        .toList();
                for (TxnOperationOsrEntity osr : osrs) {
                    OperationOnStandardResource oosr = new OperationOnStandardResource(
                            OntologyIds.operationOnStandardResourceId(txnOp.operationId, osr.standardResourceCode),
                            txnOp.operationId,
                            osr.standardResourceCode,
                            OperationResourceBinding.defaultPriority(osr.resourcePriority),
                            osr.setupTimeMinutes,
                            OperationResourceBinding.processTimeSeconds(
                                    osr.processTimeSeconds != null ? osr.processTimeSeconds : BigDecimal.valueOf(60)));
                    builder.operationOnStandardResource(oosr);
                    if (osr.resourcePriority == 1 || osrs.indexOf(osr) == 0) {
                        OperationResourceBinding.applyPrimaryTiming(
                                operation, oosr, supplyOrder.getQuantity());
                    }
                }
            }
        }
    }
}
