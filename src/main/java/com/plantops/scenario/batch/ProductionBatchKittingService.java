package com.plantops.scenario.batch;

import com.plantops.api.dto.WorkOrderKittingLineDto;
import com.plantops.api.dto.batch.InventoryBatchAllocationDto;
import com.plantops.api.dto.batch.ProductionBatchKittingDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.ProductionBatchEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.KittingService;
import com.plantops.scenario.RuleScopeHelper;
import com.plantops.scenario.WorkOrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProductionBatchKittingService {

    @Inject
    KittingService kittingService;

    @Inject
    RuleScopeHelper ruleScopeHelper;

    @Inject
    ProductionBatchSplitService batchSplitService;

    public List<ProductionBatchKittingDto> listPendingBatchKitting() {
        Map<String, WorkOrderEntity> workOrders = new HashMap<>();
        List<ProductionBatchEntity> batches = listPendingBatches(workOrders);
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        List<ProductionBatchKittingDto> out = new ArrayList<>(batches.size());
        for (ProductionBatchEntity batch : batches) {
            WorkOrderEntity wo = workOrders.get(batch.workOrderNo);
            if (wo == null) {
                continue;
            }
            BigDecimal runQty = batch.quantity != null ? batch.quantity : BigDecimal.ZERO;
            List<WorkOrderKittingLineDto> lines = computeLines(wo, runQty, pool);
            boolean kitted = kittingService.canDetailScheduleKit(wo, runQty, pool);
            String status = batch.kittingStatus != null ? batch.kittingStatus : ProductionBatchEntity.KITTING_UNKNOWN;
            if (ProductionBatchEntity.KITTING_UNKNOWN.equals(status)) {
                status = kitted ? ProductionBatchEntity.KITTING_KITTED : ProductionBatchEntity.KITTING_SHORT;
            }
            out.add(toDto(batch, wo, status, lines));
            if (kitted) {
                kittingService.consumeDetailScheduleKitting(wo, runQty, pool);
            }
        }
        return out;
    }

    @Transactional
    public List<ProductionBatchKittingDto> recomputeAllPendingBatchKitting() {
        LinkedHashSet<String> workOrderNos = new LinkedHashSet<>();
        for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveOrdered()) {
            WorkOrderEntity wo = WorkOrderEntity.findByNo(batch.workOrderNo);
            if (wo != null && WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
                workOrderNos.add(batch.workOrderNo);
            }
        }
        for (String workOrderNo : workOrderNos) {
            batchSplitService.refreshKittingStatuses(workOrderNo);
        }
        return listPendingBatchKitting();
    }

    @Transactional
    public ProductionBatchKittingDto updatePendingScheduleEligible(String batchNo, boolean pendingScheduleEligible) {
        ProductionBatchEntity batch = requireActiveBatch(batchNo);
        batch.pendingScheduleEligible = pendingScheduleEligible;
        WorkOrderEntity wo = WorkOrderEntity.findByNo(batch.workOrderNo);
        if (wo == null) {
            throw new NotFoundException("工单不存在: " + batch.workOrderNo);
        }
        return listPendingBatchKitting().stream()
                .filter(dto -> batchNo.equals(dto.batchNo()))
                .findFirst()
                .orElseGet(() -> toDto(
                        batch,
                        wo,
                        batch.kittingStatus != null ? batch.kittingStatus : ProductionBatchEntity.KITTING_UNKNOWN,
                        computeLines(wo, batch.quantity, kittingService.newAvailableInventoryPool())));
    }

    public List<InventoryBatchAllocationDto> batchesUsingComponent(String componentProductCode) {
        if (componentProductCode == null || componentProductCode.isBlank()) {
            return List.of();
        }
        Map<String, WorkOrderEntity> workOrders = new HashMap<>();
        List<ProductionBatchEntity> batches = listPendingBatches(workOrders);
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        List<InventoryBatchAllocationDto> out = new ArrayList<>();
        for (ProductionBatchEntity batch : batches) {
            WorkOrderEntity wo = workOrders.get(batch.workOrderNo);
            if (wo == null) {
                continue;
            }
            BigDecimal runQty = batch.quantity != null ? batch.quantity : BigDecimal.ZERO;
            for (WorkOrderKittingLineDto line : computeLines(wo, runQty, pool)) {
                if (!componentProductCode.equals(line.componentProductCode())) {
                    continue;
                }
                String status = batch.kittingStatus != null ? batch.kittingStatus : ProductionBatchEntity.KITTING_UNKNOWN;
                out.add(new InventoryBatchAllocationDto(
                        batch.batchNo,
                        wo.workOrderNo,
                        wo.productCode,
                        runQty,
                        wo.quantity,
                        line.requiredQty(),
                        status));
            }
            if (kittingService.canDetailScheduleKit(wo, runQty, pool)) {
                kittingService.consumeDetailScheduleKitting(wo, runQty, pool);
            }
        }
        out.sort(Comparator.comparing(InventoryBatchAllocationDto::batchNo));
        return out;
    }

    private List<ProductionBatchEntity> listPendingBatches(Map<String, WorkOrderEntity> workOrdersOut) {
        List<ProductionBatchEntity> batches = new ArrayList<>();
        for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveOrdered()) {
            WorkOrderEntity wo = workOrdersOut.computeIfAbsent(
                    batch.workOrderNo, WorkOrderEntity::findByNo);
            if (wo == null || !WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
                continue;
            }
            batches.add(batch);
        }
        return batches;
    }

    private List<WorkOrderKittingLineDto> computeLines(
            WorkOrderEntity wo,
            BigDecimal runQuantity,
            Map<String, BigDecimal> pool) {
        List<WorkOrderKittingLineDto> lines = new ArrayList<>();
        BigDecimal runQty = runQuantity != null ? runQuantity : BigDecimal.ZERO;
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForDetailSchedule(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(runQty);
            BigDecimal avail = pool.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            boolean shortage = avail.compareTo(need) < 0;
            lines.add(new WorkOrderKittingLineDto(
                    bom.componentProductCode, need, avail, shortage));
        }
        if (lines.isEmpty()) {
            lines.add(new WorkOrderKittingLineDto(
                    wo.productCode,
                    runQty,
                    pool.getOrDefault(wo.productCode, BigDecimal.ZERO),
                    false));
        }
        return lines;
    }

    private ProductionBatchKittingDto toDto(
            ProductionBatchEntity batch,
            WorkOrderEntity wo,
            String kittingStatus,
            List<WorkOrderKittingLineDto> lines) {
        return new ProductionBatchKittingDto(
                batch.batchNo,
                batch.batchSeq,
                batch.quantity,
                wo.workOrderNo,
                wo.productCode,
                wo.quantity,
                kittingStatus,
                batch.pendingScheduleEligible == null || batch.pendingScheduleEligible,
                lines);
    }

    private ProductionBatchEntity requireActiveBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BadRequestException("batchNo 不能为空");
        }
        ProductionBatchEntity batch = ProductionBatchEntity.findByBatchNo(batchNo);
        if (batch == null || !ProductionBatchEntity.STATUS_ACTIVE.equals(batch.status)) {
            throw new NotFoundException("批次不存在或已取消: " + batchNo);
        }
        return batch;
    }

    private static String normalizeDispatch(WorkOrderEntity wo) {
        return wo.dispatchStatus != null ? wo.dispatchStatus : WorkOrderService.DISPATCH_PENDING;
    }
}
