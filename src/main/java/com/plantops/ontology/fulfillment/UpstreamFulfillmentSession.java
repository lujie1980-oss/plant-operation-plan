package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MrpExplosionService;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.RuleScopeHelper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次上游建链会话：BOM 闭包、路由缓存、工单号/序号分配，避免重复 DB 往返。
 */
public final class UpstreamFulfillmentSession {

    private final BomClosureIndex bomIndex;
    private final Set<String> knownWorkOrderNos;
    private final Map<String, List<ProductRoutingSteps.Operation>> routingByProduct;
    private final Map<String, Boolean> hasRoutingByProduct;
    private final Map<String, String> primaryResourceByProduct;
    private int nextSequenceNo;

    private UpstreamFulfillmentSession(
            BomClosureIndex bomIndex,
            Set<String> knownWorkOrderNos,
            int nextSequenceNo,
            Map<String, String> primaryResourceByProduct) {
        this.bomIndex = bomIndex;
        this.knownWorkOrderNos = knownWorkOrderNos;
        this.nextSequenceNo = nextSequenceNo;
        this.routingByProduct = new HashMap<>();
        this.hasRoutingByProduct = new HashMap<>();
        this.primaryResourceByProduct = primaryResourceByProduct;
    }

    public static UpstreamFulfillmentSession create(
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            Set<String> workOrderNosSnapshot,
            RuleScopeHelper ruleScopeHelper) {
        String finished = resolveFinishedProduct(deliveryKey);
        BomClosureIndex bomIndex = BomClosureIndex.forFinishedProduct(finished, ruleScopeHelper);
        Set<String> known = new HashSet<>(workOrderNosSnapshot);
        int nextSeq = WorkOrderEntity.nextSequenceNo();
        Map<String, String> resourceByProduct = indexPrimaryResources(bomIndex.productClosure());
        return new UpstreamFulfillmentSession(bomIndex, known, nextSeq, resourceByProduct);
    }

    private static String resolveFinishedProduct(OntologyIds.CustomerOrderLineDeliveryKey deliveryKey) {
        if (deliveryKey == null) {
            return null;
        }
        SalesOrderLineEntity line =
                SalesOrderLineEntity.findByKey(deliveryKey.salesOrderNo(), deliveryKey.salesOrderLineNo());
        return line != null ? line.productCode : null;
    }

    private static Map<String, String> indexPrimaryResources(Set<String> productCodes) {
        Map<String, String> out = new HashMap<>();
        if (productCodes.isEmpty()) {
            return out;
        }
        for (ProductResourceEntity row : ProductResourceEntity.listInWorkspace()) {
            if (row.productCode == null || row.resourceId == null) {
                continue;
            }
            out.putIfAbsent(row.productCode, row.resourceId);
        }
        return out;
    }

    public BomClosureIndex bomIndex() {
        return bomIndex;
    }

    public boolean isRelevantOpenWorkOrder(
            WorkOrderEntity wo,
            OntologyIds.CustomerOrderLineDeliveryKey deliveryKey,
            Set<String> linePeggedWorkOrderNos) {
        if (wo == null || wo.productCode == null) {
            return false;
        }
        if (bomIndex.containsProduct(wo.productCode)) {
            return true;
        }
        if (linePeggedWorkOrderNos != null && linePeggedWorkOrderNos.contains(wo.workOrderNo)) {
            return true;
        }
        if (deliveryKey != null
                && deliveryKey.salesOrderNo().equals(wo.salesOrderNo)
                && deliveryKey.salesOrderLineNo() == wo.salesOrderLineNo) {
            return true;
        }
        return false;
    }

    public List<BomComponentEntity> bomChildren(String parentProductCode) {
        return bomIndex.children(parentProductCode);
    }

    public List<ProductRoutingSteps.Operation> routingFor(String productCode) {
        return routingByProduct.computeIfAbsent(productCode, ProductRoutingSteps::operationsForProduct);
    }

    public boolean hasManufacturingRouting(String productCode) {
        return hasRoutingByProduct.computeIfAbsent(
                productCode,
                pc -> !routingFor(pc).isEmpty() || ProductResourceEntity.hasRouting(pc));
    }

    public String primaryResourceId(String productCode) {
        String cached = primaryResourceByProduct.get(productCode);
        return cached != null ? cached : "UNKNOWN";
    }

    public String allocateWorkOrderNo(String productCode, LocalDate needDate) {
        String woNo = MrpExplosionService.allocateUniqueWorkOrderNo(productCode, needDate, knownWorkOrderNos);
        knownWorkOrderNos.add(woNo);
        return woNo;
    }

    public int nextSequenceNo() {
        return nextSequenceNo++;
    }

    public Set<String> knownWorkOrderNos() {
        return knownWorkOrderNos;
    }
}
