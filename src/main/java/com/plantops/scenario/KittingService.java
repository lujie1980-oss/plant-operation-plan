package com.plantops.scenario;

import com.plantops.api.dto.KittingResultDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
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
        entity.salesOrderNo = wo.salesOrderNo;
        entity.salesOrderLineNo = wo.salesOrderLineNo;
        entity.workOrderNo = wo.workOrderNo;
        entity.kittingStatus = status;
        entity.shortageReason = reason;
        entity.stampWorkspace();
        entity.persist();
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
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForDetailSchedule(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity != null ? wo.quantity : BigDecimal.ZERO);
            BigDecimal avail = available.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) {
                return false;
            }
        }
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForDetailSchedule(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity != null ? wo.quantity : BigDecimal.ZERO);
            String component = bom.componentProductCode;
            available.put(component, available.getOrDefault(component, BigDecimal.ZERO).subtract(need));
        }
        return true;
    }
}
