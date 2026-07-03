package com.plantops.scenario;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.fulfillment.UpstreamFulfillmentSession;
import com.plantops.ontology.supply.BomDependency;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将本体上游满足链中新创建的 {@link SupplyOrder}（id = MRP 工单号）落库为 {@link WorkOrderEntity}，
 * 并写入 pegging 与 BOM 依赖，供生产计划页与刷新后的满足链投影复用。
 */
@ApplicationScoped
public class OntologyUpstreamChainWorkOrderPersister {

    @Inject
    OrderDemandCancelPlanService orderDemandCancelPlanService;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int persistNewSupplyOrders(
            OntologyGraph.Builder builder,
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            Set<String> workOrderNosBeforeBuild,
            UpstreamFulfillmentSession session) {
        if (deliveryKey == null) {
            return 0;
        }

        List<SupplyOrder> newOrders = builder.supplyOrdersById().values().stream()
                .filter(so -> !workOrderNosBeforeBuild.contains(so.getId()))
                .toList();
        if (newOrders.isEmpty()) {
            return 0;
        }

        Map<String, String> parentByChild = indexParents(builder.bomDependencies());
        Map<String, Integer> bomLevelById = computeBomLevels(newOrders, parentByChild);
        List<SupplyOrder> ordered = newOrders.stream()
                .sorted(Comparator.comparingInt(so -> bomLevelById.getOrDefault(so.getId(), 0)))
                .toList();

        String finishedProductCode = resolveFinishedProductCode(builder, deliveryKey);
        List<WorkOrderEntity> workOrders = new ArrayList<>(ordered.size());
        List<WorkOrderPeggingEntity> peggingRows = new ArrayList<>(ordered.size());
        List<WorkOrderBomDependencyEntity> bomDeps = new ArrayList<>();

        for (SupplyOrder supplyOrder : ordered) {
            String parentWo = parentByChild.get(supplyOrder.getId());
            int bomLevel = bomLevelById.getOrDefault(supplyOrder.getId(), 0);
            int sequenceNo = session != null ? session.nextSequenceNo() : WorkOrderEntity.nextSequenceNo();
            workOrders.add(buildWorkOrder(supplyOrder, parentWo, bomLevel, sequenceNo, session));
            peggingRows.add(buildPegging(supplyOrder, deliveryKey, finishedProductCode));
            if (parentWo != null) {
                bomDeps.add(buildBomDependency(parentWo, supplyOrder.getId()));
            }
        }

        for (WorkOrderEntity wo : workOrders) {
            wo.persist();
        }
        for (WorkOrderPeggingEntity peg : peggingRows) {
            peg.persist();
        }
        for (WorkOrderBomDependencyEntity dep : bomDeps) {
            dep.persist();
        }
        WorkOrderEntity.getEntityManager().flush();
        return workOrders.size();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void prepareOrderLineRebuild(OntologyIds.CustomerOrderLineDeliveryKey deliveryKey) {
        if (deliveryKey == null) {
            return;
        }
        orderDemandCancelPlanService.removeExclusiveRegeneratableWorkOrders(
                deliveryKey.salesOrderNo(), deliveryKey.salesOrderLineNo());
    }

    private static Map<String, String> indexParents(List<BomDependency> dependencies) {
        Map<String, String> parentByChild = new HashMap<>();
        for (BomDependency dep : dependencies) {
            parentByChild.put(dep.getChildSupplyOrderId(), dep.getParentSupplyOrderId());
        }
        return parentByChild;
    }

    private static Map<String, Integer> computeBomLevels(
            List<SupplyOrder> orders, Map<String, String> parentByChild) {
        Map<String, Integer> levels = new HashMap<>();
        for (SupplyOrder order : orders) {
            levels.put(order.getId(), depth(order.getId(), parentByChild, levels, new HashSet<>()));
        }
        return levels;
    }

    private static int depth(
            String supplyOrderId,
            Map<String, String> parentByChild,
            Map<String, Integer> memo,
            Set<String> visiting) {
        Integer cached = memo.get(supplyOrderId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(supplyOrderId)) {
            return 0;
        }
        String parent = parentByChild.get(supplyOrderId);
        int level = parent == null ? 0 : depth(parent, parentByChild, memo, visiting) + 1;
        memo.put(supplyOrderId, level);
        visiting.remove(supplyOrderId);
        return level;
    }

    private static WorkOrderEntity buildWorkOrder(
            SupplyOrder supplyOrder,
            String parentWorkOrderNo,
            int bomLevel,
            int sequenceNo,
            UpstreamFulfillmentSession session) {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = supplyOrder.getId();
        wo.ensureWorkspace();
        wo.salesOrderNo = null;
        wo.salesOrderLineNo = 0;
        wo.productCode = supplyOrder.getProductCode();
        wo.quantity = BigDecimal.valueOf(supplyOrder.getQuantity());
        wo.resourceId = session != null
                ? session.primaryResourceId(supplyOrder.getProductCode())
                : resolveResourceId(supplyOrder.getProductCode());
        wo.sequenceNo = sequenceNo;
        wo.parentWorkOrderNo = parentWorkOrderNo;
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        wo.needDate = supplyOrder.getNeedDate();
        wo.bomLevel = bomLevel;
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.pendingScheduleEligible = Boolean.TRUE;
        wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_NONE;
        return wo;
    }

    private static WorkOrderPeggingEntity buildPegging(
            SupplyOrder supplyOrder,
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            String finishedProductCode) {
        WorkOrderPeggingEntity peg = new WorkOrderPeggingEntity();
        peg.workOrderNo = supplyOrder.getId();
        peg.salesOrderNo = deliveryKey.salesOrderNo();
        peg.salesOrderLineNo = deliveryKey.salesOrderLineNo();
        peg.finishedProductCode = finishedProductCode;
        peg.peggedQty = BigDecimal.valueOf(supplyOrder.getQuantity());
        peg.needDate = supplyOrder.getNeedDate();
        peg.ensureWorkspace();
        return peg;
    }

    private static WorkOrderBomDependencyEntity buildBomDependency(String parentWorkOrderNo, String childWorkOrderNo) {
        WorkOrderBomDependencyEntity dep = new WorkOrderBomDependencyEntity();
        dep.parentWorkOrderNo = parentWorkOrderNo;
        dep.childWorkOrderNo = childWorkOrderNo;
        dep.ensureWorkspace();
        return dep;
    }

    private static String resolveResourceId(String productCode) {
        ProductResourceEntity pr = ProductResourceEntity.findFirstByProduct(productCode);
        return pr != null ? pr.resourceId : "UNKNOWN";
    }

    private static String resolveFinishedProductCode(
            OntologyGraph.Builder builder, OntologyIds.CustomerOrderLineDeliveryKey deliveryKey) {
        String fromGraph = deliveryKey.finishedProductCode(builder);
        if (fromGraph != null && !fromGraph.isBlank()) {
            return fromGraph;
        }
        SalesOrderLineEntity line =
                SalesOrderLineEntity.findByKey(deliveryKey.salesOrderNo(), deliveryKey.salesOrderLineNo());
        return line != null ? line.productCode : deliveryKey.salesOrderNo();
    }
}
