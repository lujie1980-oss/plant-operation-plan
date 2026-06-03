package com.plantops.scenario.batch;

import com.plantops.api.dto.batch.BatchCancelRequestDto;
import com.plantops.api.dto.batch.BatchPlanWorkOrderDto;
import com.plantops.api.dto.batch.BatchSplitResultDto;
import com.plantops.api.dto.batch.BulkBatchSplitResultDto;
import com.plantops.api.dto.batch.ProductionBatchDto;
import com.plantops.config.BatchSplitConfigService;
import com.plantops.persistence.entity.ProductionBatchEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.KittingService;
import com.plantops.scenario.WorkOrderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ProductionBatchSplitService {

    @Inject
    BatchSplitConfigService batchSplitConfig;

    @Inject
    KittingService kittingService;

    public List<BatchPlanWorkOrderDto> listWorkOrdersForBatchPlan() {
        List<BatchPlanWorkOrderDto> out = new ArrayList<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listAllOrdered()) {
            if (!WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
                continue;
            }
            out.add(toWorkOrderDto(wo));
        }
        return out;
    }

    public List<ProductionBatchDto> listBatches(String workOrderNo) {
        WorkOrderEntity wo = requireDispatchedWorkOrder(workOrderNo);
        return ProductionBatchEntity.listActiveByWorkOrder(wo.workOrderNo).stream()
                .map(this::toBatchDto)
                .toList();
    }

    @Transactional
    public BatchSplitResultDto autoSplit(String workOrderNo) {
        WorkOrderEntity wo = requireDispatchedWorkOrder(workOrderNo);
        BatchSplitMode mode = batchSplitConfig.mode();
        if (mode == BatchSplitMode.NONE) {
            throw new BadRequestException("当前批次策略为「不拆批次」，请在计划参数中调整 batch_split_mode");
        }
        return switch (mode) {
            case FIXED_QTY -> splitByFixedQuantity(wo);
            case KITTING -> splitByKitting(wo);
            case AUTO -> splitByAuto(wo);
            default -> throw new BadRequestException("不支持的拆批策略: " + mode);
        };
    }

    public BulkBatchSplitResultDto autoSplitAll() {
        BatchSplitMode mode = batchSplitConfig.mode();
        if (mode == BatchSplitMode.NONE) {
            throw new BadRequestException("当前批次策略为「不拆批次」，请在计划参数 · 批次拆解中配置策略");
        }
        int attempted = 0;
        int succeeded = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (BatchPlanWorkOrderDto wo : listWorkOrdersForBatchPlan()) {
            if (wo.remainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }
            attempted++;
            try {
                autoSplit(wo.workOrderNo());
                succeeded++;
            } catch (RuntimeException e) {
                failures.add(wo.workOrderNo() + ": " + e.getMessage());
            }
        }
        return new BulkBatchSplitResultDto(attempted, succeeded, skipped, failures);
    }

    /**
     * 「不拆批次」策略：工单下发后为整单创建唯一默认批次（幂等）。
     * 仅在 {@link BatchSplitMode#NONE} 时由下发流程调用。
     */
    @Transactional
    public void ensureDefaultBatchOnDispatch(String workOrderNo) {
        if (batchSplitConfig.mode() != BatchSplitMode.NONE) {
            return;
        }
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null || !WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
            return;
        }
        if (remainingQuantity(wo).compareTo(BigDecimal.ZERO) <= 0) {
            refreshWorkOrderSplitState(wo);
            return;
        }
        BigDecimal qty = wo.quantity != null ? wo.quantity : BigDecimal.ZERO;
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        reserveExistingBatches(wo, pool);
        boolean kitted = kittingService.canDetailScheduleKit(wo, qty, pool);
        createBatch(
                wo,
                qty.setScale(4, RoundingMode.HALF_UP),
                ProductionBatchEntity.SPLIT_WHOLE,
                kitted ? ProductionBatchEntity.KITTING_KITTED : ProductionBatchEntity.KITTING_SHORT);
        refreshWorkOrderSplitState(wo);
    }

    @Transactional
    public BatchSplitResultDto refreshKittingStatuses(String workOrderNo) {
        WorkOrderEntity wo = requireDispatchedWorkOrder(workOrderNo);
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveByWorkOrder(wo.workOrderNo)) {
            boolean kitted = kittingService.canDetailScheduleKit(wo, batch.quantity, pool);
            batch.kittingStatus = kitted
                    ? ProductionBatchEntity.KITTING_KITTED
                    : ProductionBatchEntity.KITTING_SHORT;
            if (kitted) {
                kittingService.consumeDetailScheduleKitting(wo, batch.quantity, pool);
            }
        }
        return result(wo);
    }

    @Transactional
    public BatchSplitResultDto manualSplit(String workOrderNo, BigDecimal quantity) {
        WorkOrderEntity wo = requireDispatchedWorkOrder(workOrderNo);
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("批次数量必须大于 0");
        }
        BigDecimal remaining = remainingQuantity(wo);
        if (quantity.compareTo(remaining) > 0) {
            throw new BadRequestException(
                    "批次数量 " + quantity + " 超过剩余可拆量 " + remaining);
        }
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        reserveExistingBatches(wo, pool);
        boolean kitted = kittingService.canDetailScheduleKit(wo, quantity, pool);
        createBatch(
                wo,
                quantity.setScale(4, RoundingMode.HALF_UP),
                ProductionBatchEntity.SPLIT_MANUAL,
                kitted ? ProductionBatchEntity.KITTING_KITTED : ProductionBatchEntity.KITTING_SHORT);
        refreshWorkOrderSplitState(wo);
        return result(wo);
    }

    @Transactional
    public BatchSplitResultDto cancel(BatchCancelRequestDto request) {
        if (request == null) {
            throw new BadRequestException("请求体不能为空");
        }
        if (request.cancelAll()) {
            if (request.workOrderNo() == null || request.workOrderNo().isBlank()) {
                throw new BadRequestException("取消全部批次需提供 workOrderNo");
            }
            WorkOrderEntity wo = requireDispatchedWorkOrder(request.workOrderNo());
            for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveByWorkOrder(wo.workOrderNo)) {
                batch.status = ProductionBatchEntity.STATUS_CANCELLED;
            }
            refreshWorkOrderSplitState(wo);
            return result(wo);
        }
        if (request.batchNo() == null || request.batchNo().isBlank()) {
            throw new BadRequestException("需提供 batchNo 或 cancelAll=true");
        }
        ProductionBatchEntity batch = ProductionBatchEntity.findByBatchNo(request.batchNo());
        if (batch == null || !ProductionBatchEntity.STATUS_ACTIVE.equals(batch.status)) {
            throw new NotFoundException("批次不存在或已取消: " + request.batchNo());
        }
        WorkOrderEntity wo = requireDispatchedWorkOrder(batch.workOrderNo);
        batch.status = ProductionBatchEntity.STATUS_CANCELLED;
        refreshWorkOrderSplitState(wo);
        return result(wo);
    }

    private BatchSplitResultDto splitByFixedQuantity(WorkOrderEntity wo) {
        BigDecimal remaining = remainingQuantity(wo);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("工单无可拆剩余量: " + wo.workOrderNo);
        }
        BigDecimal batchSize = BigDecimal.valueOf(batchSplitConfig.fixedQty());
        List<BigDecimal> quantities = BatchFixedQuantitySplitter.split(
                remaining, batchSize, batchSplitConfig.remainderMode());
        if (quantities.isEmpty()) {
            throw new BadRequestException("固定批量拆批未产生批次（请检查批量与余数模式）");
        }
        for (BigDecimal qty : quantities) {
            createBatch(wo, qty, ProductionBatchEntity.SPLIT_FIXED, ProductionBatchEntity.KITTING_UNKNOWN);
        }
        refreshWorkOrderSplitState(wo);
        return result(wo);
    }

    private BatchSplitResultDto splitByAuto(WorkOrderEntity wo) {
        BigDecimal remaining = remainingQuantity(wo);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("工单无可拆剩余量: " + wo.workOrderNo);
        }
        int targetSize = BatchAutoSplitPlanner.computeTargetBatchSize(
                wo,
                remaining,
                batchSplitConfig.fixedQty(),
                batchSplitConfig.minQty(),
                batchSplitConfig.maxQty(),
                BatchSplitCapacityHelper.perShiftMinutesForProduct(wo.productCode));
        List<BigDecimal> quantities = BatchAutoSplitPlanner.planQuantities(
                remaining,
                targetSize,
                batchSplitConfig.minQty(),
                batchSplitConfig.maxQty());
        if (quantities.isEmpty()) {
            throw new BadRequestException("自动拆批未产生批次");
        }
        createBatchesWithKittingEvaluation(wo, quantities, ProductionBatchEntity.SPLIT_AUTO);
        refreshWorkOrderSplitState(wo);
        return result(wo);
    }

    /** 按序评估齐套并创建批次；未齐套且未启用 SHORT 时停止，剩余量留父工单。 */
    private void createBatchesWithKittingEvaluation(
            WorkOrderEntity wo,
            List<BigDecimal> quantities,
            String splitMethod) {
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        reserveExistingBatches(wo, pool);
        for (BigDecimal qty : quantities) {
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (kittingService.canDetailScheduleKit(wo, qty, pool)) {
                createBatch(wo, qty, splitMethod, ProductionBatchEntity.KITTING_KITTED);
                kittingService.consumeDetailScheduleKitting(wo, qty, pool);
            } else if (batchSplitConfig.kittingCreateShortBatch()) {
                createBatch(wo, qty, splitMethod, ProductionBatchEntity.KITTING_SHORT);
            } else {
                break;
            }
        }
    }

    private BatchSplitResultDto splitByKitting(WorkOrderEntity wo) {
        BigDecimal remaining = remainingQuantity(wo);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("工单无可拆剩余量: " + wo.workOrderNo);
        }
        Map<String, BigDecimal> pool = kittingService.newAvailableInventoryPool();
        reserveExistingBatches(wo, pool);
        BigDecimal kittedQty = kittingService.maxDetailScheduleKittingQuantity(wo, remaining, pool);
        if (kittedQty.compareTo(BigDecimal.ZERO) > 0) {
            createBatch(wo, kittedQty, ProductionBatchEntity.SPLIT_KITTING, ProductionBatchEntity.KITTING_KITTED);
            kittingService.consumeDetailScheduleKitting(wo, kittedQty, pool);
        }
        BigDecimal shortQty = remaining.subtract(kittedQty).setScale(4, RoundingMode.HALF_UP);
        if (shortQty.compareTo(BigDecimal.ZERO) > 0) {
            if (batchSplitConfig.kittingCreateShortBatch()) {
                createBatch(wo, shortQty, ProductionBatchEntity.SPLIT_KITTING, ProductionBatchEntity.KITTING_SHORT);
            }
        } else if (kittedQty.compareTo(BigDecimal.ZERO) <= 0) {
            if (batchSplitConfig.kittingCreateShortBatch()) {
                createBatch(wo, remaining, ProductionBatchEntity.SPLIT_KITTING, ProductionBatchEntity.KITTING_SHORT);
            } else {
                throw new BadRequestException(
                        "当前库存无法齐套任何数量；可在计划参数中启用 batch_kitting_create_short_batch 以创建缺料批次");
            }
        }
        refreshWorkOrderSplitState(wo);
        return result(wo);
    }

    private void reserveExistingBatches(WorkOrderEntity wo, Map<String, BigDecimal> pool) {
        for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveByWorkOrder(wo.workOrderNo)) {
            if (ProductionBatchEntity.KITTING_KITTED.equals(batch.kittingStatus)
                    || ProductionBatchEntity.KITTING_UNKNOWN.equals(batch.kittingStatus)) {
                kittingService.consumeDetailScheduleKitting(wo, batch.quantity, pool);
            }
        }
    }

    private BatchSplitResultDto result(WorkOrderEntity wo) {
        return new BatchSplitResultDto(
                wo.workOrderNo,
                wo.batchSplitStatus,
                remainingQuantity(wo),
                listBatches(wo.workOrderNo));
    }

    private void createBatch(
            WorkOrderEntity wo,
            BigDecimal quantity,
            String splitMethod,
            String kittingStatus) {
        int seq = ProductionBatchEntity.nextBatchSeq(wo.workOrderNo);
        ProductionBatchEntity batch = new ProductionBatchEntity();
        batch.batchNo = formatBatchNo(wo.workOrderNo, seq);
        batch.workOrderNo = wo.workOrderNo;
        batch.batchSeq = seq;
        batch.quantity = quantity;
        batch.splitMethod = splitMethod;
        batch.kittingStatus = kittingStatus != null ? kittingStatus : ProductionBatchEntity.KITTING_UNKNOWN;
        batch.status = ProductionBatchEntity.STATUS_ACTIVE;
        batch.pendingScheduleEligible = Boolean.TRUE;
        batch.createdTs = LocalDateTime.now();
        batch.stampWorkspace();
        batch.persist();
    }

    static String formatBatchNo(String workOrderNo, int seq) {
        return "BAT-" + workOrderNo + "-" + String.format("%02d", seq);
    }

    private void refreshWorkOrderSplitState(WorkOrderEntity wo) {
        BigDecimal batched = ProductionBatchEntity.sumActiveQuantity(wo.workOrderNo);
        BigDecimal total = wo.quantity != null ? wo.quantity : BigDecimal.ZERO;
        if (batched.compareTo(BigDecimal.ZERO) <= 0) {
            wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_NONE;
            wo.pendingScheduleEligible = Boolean.TRUE;
            return;
        }
        wo.pendingScheduleEligible = Boolean.FALSE;
        if (batched.compareTo(total) >= 0) {
            wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_SPLIT;
        } else {
            wo.batchSplitStatus = WorkOrderEntity.BATCH_SPLIT_PARTIAL;
        }
    }

    static BigDecimal remainingQuantity(WorkOrderEntity wo) {
        BigDecimal total = wo.quantity != null ? wo.quantity : BigDecimal.ZERO;
        BigDecimal batched = ProductionBatchEntity.sumActiveQuantity(wo.workOrderNo);
        return total.subtract(batched).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private WorkOrderEntity requireDispatchedWorkOrder(String workOrderNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("工单不存在: " + workOrderNo);
        }
        if (!WorkOrderService.DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
            throw new BadRequestException("仅已下发工单可拆批: " + workOrderNo);
        }
        return wo;
    }

    private static String normalizeDispatch(WorkOrderEntity wo) {
        return wo.dispatchStatus != null ? wo.dispatchStatus : WorkOrderService.DISPATCH_PENDING;
    }

    private BatchPlanWorkOrderDto toWorkOrderDto(WorkOrderEntity wo) {
        BigDecimal total = wo.quantity != null ? wo.quantity : BigDecimal.ZERO;
        BigDecimal batched = ProductionBatchEntity.sumActiveQuantity(wo.workOrderNo);
        return new BatchPlanWorkOrderDto(
                wo.workOrderNo,
                wo.productCode,
                total,
                batched,
                total.subtract(batched).max(BigDecimal.ZERO),
                wo.batchSplitStatus != null ? wo.batchSplitStatus : WorkOrderEntity.BATCH_SPLIT_NONE,
                WorkOrderService.isPendingScheduleEligible(wo),
                normalizeDispatch(wo));
    }

    private ProductionBatchDto toBatchDto(ProductionBatchEntity batch) {
        return new ProductionBatchDto(
                batch.id,
                batch.batchNo,
                batch.workOrderNo,
                batch.batchSeq,
                batch.quantity,
                batch.kittingStatus,
                batch.splitMethod,
                batch.status,
                batch.pendingScheduleEligible != null && batch.pendingScheduleEligible,
                batch.createdTs);
    }
}
