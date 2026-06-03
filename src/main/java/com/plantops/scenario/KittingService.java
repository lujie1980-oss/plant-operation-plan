package com.plantops.scenario;

import com.plantops.api.dto.KittingResultDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import com.plantops.scenario.planning.InventorySnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class KittingService {

    @Inject
    RuleScopeHelper ruleScopeHelper;

    @Transactional
    public List<KittingResultDto> compute() {
        KittingResultEntity.delete("workspaceId", KittingResultEntity.ws());
        List<KittingResultDto> results = new ArrayList<>();
        Map<String, BigDecimal> available = newAvailableInventoryPool();

        for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {
            if ("CANCELLED".equals(order.status)) {
                continue;
            }
            String status = "KITTING_OK";
            String reason = null;
            for (BomComponentEntity bom : BomComponentEntity.findChildren(order.productCode, order.productCode)) {
                if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                    continue;
                }
                BigDecimal need = bom.componentQty.multiply(order.orderQty);
                BigDecimal avail = available.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
                if (avail.compareTo(need) < 0) {
                    status = "SHORTAGE";
                    reason = "Material shortage: " + bom.componentProductCode
                            + " need=" + need + " avail=" + avail;
                    break;
                }
                available.put(bom.componentProductCode, avail.subtract(need));
            }

            KittingResultEntity entity = new KittingResultEntity();
            entity.computedTs = LocalDateTime.now();
            entity.salesOrderNo = order.salesOrderNo;
            entity.salesOrderLineNo = order.salesOrderLineNo;
            entity.kittingStatus = status;
            entity.shortageReason = reason;
            entity.stampWorkspace();
            entity.persist();

            results.add(new KittingResultDto(order.salesOrderNo, order.salesOrderLineNo, status, reason));
        }
        return results;
    }

    @Transactional
    public void computeForWorkOrders(List<String> workOrderNos) {
        if (workOrderNos == null || workOrderNos.isEmpty()) {
            return;
        }
        for (String woNo : workOrderNos) {
            WorkOrderEntity wo = WorkOrderEntity.findByNo(woNo);
            if (wo == null) {
                continue;
            }
            persistWorkOrderKitting(wo);
        }
    }

    private void persistWorkOrderKitting(WorkOrderEntity wo) {
        Map<String, BigDecimal> available = newAvailableInventoryPool();
        String status = "KITTING_OK";
        String reason = null;
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity);
            BigDecimal avail = available.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) {
                status = "SHORTAGE";
                reason = "Material shortage: " + bom.componentProductCode
                        + " need=" + need + " avail=" + avail;
                break;
            }
            available.put(bom.componentProductCode, avail.subtract(need));
        }
        KittingResultEntity entity = new KittingResultEntity();
        entity.computedTs = LocalDateTime.now();
        SalesOrderRef orderRef = resolveSalesOrderRef(wo);
        entity.salesOrderNo = orderRef.salesOrderNo();
        entity.salesOrderLineNo = orderRef.salesOrderLineNo();
        entity.workOrderNo = wo.workOrderNo;
        entity.kittingStatus = status;
        entity.shortageReason = reason;
        entity.stampWorkspace();
        entity.persist();
    }

    /** 工单齐套结果需写入非空 sales_order_no；组件工单从 pegging 或父工单链解析。 */
    private SalesOrderRef resolveSalesOrderRef(WorkOrderEntity wo) {
        WorkOrderEntity current = wo;
        while (current != null) {
            if (current.salesOrderNo != null && !current.salesOrderNo.isBlank()) {
                return new SalesOrderRef(current.salesOrderNo, current.salesOrderLineNo);
            }
            for (WorkOrderPeggingEntity peg : WorkOrderPeggingEntity.findByWorkOrder(current.workOrderNo)) {
                if (peg.salesOrderNo != null && !peg.salesOrderNo.isBlank()) {
                    return new SalesOrderRef(peg.salesOrderNo, peg.salesOrderLineNo);
                }
            }
            if (current.parentWorkOrderNo == null || current.parentWorkOrderNo.isBlank()) {
                break;
            }
            current = WorkOrderEntity.findByNo(current.parentWorkOrderNo);
        }
        return new SalesOrderRef("WO:" + wo.workOrderNo, 0);
    }

    private record SalesOrderRef(String salesOrderNo, int salesOrderLineNo) {
    }

    public boolean isEligible(String salesOrderNo, int lineNo) {
        KittingResultEntity r = KittingResultEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2 order by computedTs desc",
                        salesOrderNo, lineNo)
                .firstResult();
        return r == null || "KITTING_OK".equals(r.kittingStatus);
    }

    public Map<String, BigDecimal> newAvailableInventoryPool() {
        return newAvailableInventoryPool(InventorySnapshot.loadFromWorkspace());
    }

    /** 基于统一库存快照创建 S05 齐套消耗池（可变副本）。 */
    public Map<String, BigDecimal> newAvailableInventoryPool(InventorySnapshot inventorySnapshot) {
        if (inventorySnapshot == null) {
            return newAvailableInventoryPool();
        }
        return inventorySnapshot.newMutablePool();
    }

    /**
     * 按 BOM 检查并消耗库存池；供 S05 推演层顺序齐套使用。
     */
    public boolean checkAndConsumeWorkOrderKitting(WorkOrderEntity wo, Map<String, BigDecimal> available) {
        if (wo == null || available == null) {
            return true;
        }
        return checkAndConsumeWorkOrderKitting(
                wo, wo.quantity != null ? wo.quantity : BigDecimal.ZERO, available);
    }

    /**
     * 按指定生产量检查并消耗库存池（S05 批次齐套）。
     */
    public boolean checkAndConsumeWorkOrderKitting(
            WorkOrderEntity wo, BigDecimal runQuantity, Map<String, BigDecimal> available) {
        if (wo == null || available == null) {
            return true;
        }
        BigDecimal qty = runQuantity != null ? runQuantity : BigDecimal.ZERO;
        if (!canDetailScheduleKit(wo, qty, available)) {
            return false;
        }
        consumeDetailScheduleKitting(wo, qty, available);
        return true;
    }

    /** 当前库存池下最多可齐套生产的数量（不超过 capQuantity）。 */
    public BigDecimal maxDetailScheduleKittingQuantity(
            WorkOrderEntity wo, BigDecimal capQuantity, Map<String, BigDecimal> available) {
        return com.plantops.scenario.batch.BatchKittingQuantityCalculator.maxKittingQuantity(
                wo, capQuantity, available, ruleScopeHelper);
    }

    public boolean canDetailScheduleKit(
            WorkOrderEntity wo, BigDecimal runQuantity, Map<String, BigDecimal> available) {
        return com.plantops.scenario.batch.BatchKittingQuantityCalculator.canKitQuantity(
                wo, runQuantity, available, ruleScopeHelper);
    }

    public void consumeDetailScheduleKitting(
            WorkOrderEntity wo, BigDecimal runQuantity, Map<String, BigDecimal> available) {
        com.plantops.scenario.batch.BatchKittingQuantityCalculator.consumeForQuantity(
                wo, runQuantity, available, ruleScopeHelper);
    }
}
