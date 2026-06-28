package com.plantops.scenario;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderDispatchRequestDto;
import com.plantops.api.dto.WorkOrderDispatchResultDto;
import com.plantops.api.dto.WorkOrderDto;
import com.plantops.api.dto.WorkOrderKittingDto;
import com.plantops.api.dto.WorkOrderKittingLineDto;
import com.plantops.api.dto.WorkOrderScheduleOperationDto;
import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.api.dto.InventoryAvailabilitySummaryDto;
import com.plantops.api.dto.InventoryWorkOrderAllocationDto;
import com.plantops.api.dto.WorkOrderPendingScheduleEligibleRequestDto;
import com.plantops.config.BatchSplitConfigService;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.scenario.batch.BatchSplitMode;
import com.plantops.scenario.batch.ProductionBatchSplitService;
import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.ProductionBatchEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.api.dto.WorkOrderPeggingDto;
import com.plantops.api.dto.WorkOrderRoutingDetailDto;
import com.plantops.api.dto.WorkOrderRoutingOperationDto;
import com.plantops.api.dto.WorkOrderRoutingResourceOptionDto;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WorkOrderService {

    public static final String DISPATCH_PENDING = "PENDING";
    public static final String DISPATCH_DISPATCHED = "DISPATCHED";

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    KittingService kittingService;

    @Inject
    FulfillmentPeggingService fulfillmentPeggingService;

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    WorkOrderTimingService workOrderTimingService;

    @Inject
    BatchSplitConfigService batchSplitConfig;

    @Inject
    ProductionBatchSplitService productionBatchSplitService;

    public List<WorkOrderDto> listAll() {
        return listAll(null);
    }

    public List<WorkOrderDto> listAll(String masterPlanVersionId) {
        return listAll(masterPlanVersionId, null);
    }

    public List<WorkOrderDto> listAll(String masterPlanVersionId, String detailScheduleVersionId) {
        String resolvedDetailScheduleVersionId = resolveDetailScheduleVersionId(
                masterPlanVersionId, detailScheduleVersionId);
        Map<String, ScheduleFeedbackService.WorkOrderFeedbackFlags> feedbackFlags =
                scheduleFeedbackService.feedbackFlagsByWorkOrder(resolvedDetailScheduleVersionId);
        return WorkOrderEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparingInt((WorkOrderEntity w) -> w.sequenceNo)
                        .thenComparing(w -> w.workOrderNo))
                .map(wo -> toDto(wo, masterPlanVersionId, resolvedDetailScheduleVersionId, feedbackFlags))
                .toList();
    }

    public WorkOrderDto toWorkOrderDto(WorkOrderEntity wo, String masterPlanVersionId) {
        String detailScheduleVersionId = scheduleFeedbackService.resolveDetailScheduleVersionId(masterPlanVersionId);
        Map<String, ScheduleFeedbackService.WorkOrderFeedbackFlags> feedbackFlags =
                scheduleFeedbackService.feedbackFlagsByWorkOrder(detailScheduleVersionId);
        return toDto(wo, masterPlanVersionId, detailScheduleVersionId, feedbackFlags);
    }

    public List<WorkOrderScheduleOperationDto> scheduleOperations(
            String workOrderNo,
            String masterPlanVersionId) {
        return scheduleOperations(workOrderNo, masterPlanVersionId, null);
    }

    public List<WorkOrderScheduleOperationDto> scheduleOperations(
            String workOrderNo,
            String masterPlanVersionId,
            String detailScheduleVersionId) {
        String resolved = resolveDetailScheduleVersionId(masterPlanVersionId, detailScheduleVersionId);
        return scheduleFeedbackService.scheduleOperationsForWorkOrder(workOrderNo, resolved);
    }

    /** 已下发、待进入细排的工单列表。 */
    public List<WorkOrderDto> listDispatched(String masterPlanVersionId) {
        return listDispatched(masterPlanVersionId, null);
    }

    public List<WorkOrderDto> listDispatched(String masterPlanVersionId, String detailScheduleVersionId) {
        return listAll(masterPlanVersionId, detailScheduleVersionId).stream()
                .filter(wo -> DISPATCH_DISPATCHED.equals(wo.dispatchStatus()))
                .toList();
    }

    private String resolveDetailScheduleVersionId(String masterPlanVersionId, String detailScheduleVersionId) {
        if (detailScheduleVersionId != null && !detailScheduleVersionId.isBlank()) {
            return detailScheduleVersionId;
        }
        return scheduleFeedbackService.resolveDetailScheduleVersionId(masterPlanVersionId);
    }

    @Transactional
    public WorkOrderDto updatePendingScheduleEligible(String workOrderNo, boolean pendingScheduleEligible) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("工单不存在: " + workOrderNo);
        }
        if (!DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
            throw new BadRequestException("仅已下发工单可设定待排状态: " + workOrderNo);
        }
        if (hasActiveBatches(wo)) {
            for (ProductionBatchEntity batch : ProductionBatchEntity.listActiveByWorkOrder(workOrderNo)) {
                batch.pendingScheduleEligible = pendingScheduleEligible;
            }
        } else {
            wo.pendingScheduleEligible = pendingScheduleEligible;
        }
        return toWorkOrderDto(wo, null);
    }

    /** 已拆批工单以批次待排开关为准；未拆批时看工单字段。 */
    static boolean resolvePendingScheduleEligible(WorkOrderEntity wo) {
        if (wo == null) {
            return true;
        }
        if (hasActiveBatches(wo)) {
            return ProductionBatchEntity.listActiveByWorkOrder(wo.workOrderNo).stream()
                    .anyMatch(b -> b.pendingScheduleEligible == null || b.pendingScheduleEligible);
        }
        return wo.pendingScheduleEligible == null || wo.pendingScheduleEligible;
    }

    static boolean hasActiveBatches(WorkOrderEntity wo) {
        if (wo == null || wo.batchSplitStatus == null
                || WorkOrderEntity.BATCH_SPLIT_NONE.equals(wo.batchSplitStatus)) {
            return false;
        }
        return ProductionBatchEntity.sumActiveQuantity(wo.workOrderNo).compareTo(BigDecimal.ZERO) > 0;
    }

    /** 按料号汇总可用库存（齐套页）。 */
    public List<InventoryAvailabilitySummaryDto> inventoryAvailabilitySummary() {
        Map<String, BigDecimal> onhand = new HashMap<>();
        Map<String, BigDecimal> available = new HashMap<>();
        Map<String, Integer> points = new HashMap<>();
        for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
            if (inv.productCode == null || inv.productCode.isBlank()) {
                continue;
            }
            onhand.merge(inv.productCode, inv.onhandQty != null ? inv.onhandQty : BigDecimal.ZERO, BigDecimal::add);
            available.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
            points.merge(inv.productCode, 1, Integer::sum);
        }
        return available.keySet().stream()
                .sorted()
                .map(code -> new InventoryAvailabilitySummaryDto(
                        code,
                        onhand.getOrDefault(code, BigDecimal.ZERO),
                        available.getOrDefault(code, BigDecimal.ZERO),
                        points.getOrDefault(code, 0)))
                .toList();
    }

    /** 已下发工单中对某料号有需求的占用明细。 */
    public List<InventoryWorkOrderAllocationDto> workOrdersUsingComponent(String componentProductCode) {
        if (componentProductCode == null || componentProductCode.isBlank()) {
            return List.of();
        }
        List<InventoryWorkOrderAllocationDto> out = new ArrayList<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listInWorkspace()) {
            if (!DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
                continue;
            }
            WorkOrderKittingCheck check = computeKittingCheck(wo);
            for (WorkOrderKittingLineDto line : check.lines()) {
                if (!componentProductCode.equals(line.componentProductCode())) {
                    continue;
                }
                out.add(new InventoryWorkOrderAllocationDto(
                        wo.workOrderNo,
                        wo.productCode,
                        wo.quantity,
                        line.requiredQty(),
                        check.status()));
            }
        }
        out.sort(Comparator.comparing(InventoryWorkOrderAllocationDto::workOrderNo));
        return out;
    }

    /** 工单工艺路径：工序顺序、可选资源及可用产线。 */
    public WorkOrderRoutingDetailDto routingDetail(String workOrderNo, String masterPlanVersionId) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("工单不存在: " + workOrderNo);
        }
        if (!DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
            throw new BadRequestException("仅已下发工单可查看工艺路径: " + workOrderNo);
        }
        return buildRoutingDetail(wo, wo.quantity, masterPlanVersionId, null);
    }

    /** 批次工艺路径：工时按批次量计算。 */
    public WorkOrderRoutingDetailDto routingDetailForQuantity(
            String workOrderNo,
            BigDecimal quantity,
            String masterPlanVersionId,
            String batchNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("工单不存在: " + workOrderNo);
        }
        if (!DISPATCH_DISPATCHED.equals(normalizeDispatch(wo))) {
            throw new BadRequestException("仅已下发工单可查看工艺路径: " + workOrderNo);
        }
        BigDecimal effectiveQty = quantity != null ? quantity : wo.quantity;
        return buildRoutingDetail(wo, effectiveQty, masterPlanVersionId, batchNo);
    }

    private WorkOrderRoutingDetailDto buildRoutingDetail(
            WorkOrderEntity wo,
            BigDecimal runQuantity,
            String masterPlanVersionId,
            String batchNo) {
        Map<String, List<String>> lineIdsByResource = lineIdsByResourceIndex();
        List<ProductRoutingSteps.Operation> routingOps = ProductRoutingSteps.operationsForProduct(wo.productCode);
        List<WorkOrderRoutingOperationDto> operations = new ArrayList<>(routingOps.size());
        for (ProductRoutingSteps.Operation op : routingOps) {
            List<WorkOrderRoutingResourceOptionDto> options = new ArrayList<>(op.resourceOptions().size());
            for (ProductRoutingSteps.ResourceOption resource : op.resourceOptions()) {
                int duration = ProductRoutingSteps.durationMinutesForResource(
                        wo.productCode, resource.resourceId(), runQuantity);
                if (duration <= 0) {
                    duration = ProductRoutingSteps.durationMinutesForOperation(op, runQuantity);
                }
                int priority = resource.resourcePriority() != null ? resource.resourcePriority() : 1;
                List<String> lineIds = lineIdsByResource.getOrDefault(resource.resourceId(), List.of());
                options.add(new WorkOrderRoutingResourceOptionDto(
                        resource.resourceId(), priority, duration, lineIds));
            }
            operations.add(new WorkOrderRoutingOperationDto(op.sequenceNo(), op.operationName(), options));
        }
        WorkOrderDto summary = toWorkOrderDto(wo, masterPlanVersionId);
        return new WorkOrderRoutingDetailDto(
                wo.workOrderNo,
                wo.productCode,
                runQuantity,
                summary.dispatchStatus(),
                wo.dispatchedTs,
                summary.plannedSlotDate(),
                summary.plannedShiftId(),
                summary.resourceId(),
                operations,
                batchNo);
    }

    private static Map<String, List<String>> lineIdsByResourceIndex() {
        Map<String, LinkedHashSet<String>> index = new HashMap<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.resourceId == null || line.lineId == null) {
                continue;
            }
            index.computeIfAbsent(line.resourceId, k -> new LinkedHashSet<>()).add(line.lineId);
        }
        Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : index.entrySet()) {
            out.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return out;
    }

    @Transactional
    public WorkOrderDispatchResultDto dispatchForScheduling(WorkOrderDispatchRequestDto request) {
        if (request == null || request.workOrderNos() == null || request.workOrderNos().isEmpty()) {
            throw new BadRequestException("?????????");
        }
        LocalDateTime now = LocalDateTime.now();
        List<String> dispatched = new ArrayList<>();
        for (String woNo : request.workOrderNos()) {
            WorkOrderEntity wo = WorkOrderEntity.findByNo(woNo);
            if (wo == null) {
                throw new NotFoundException("?????: " + woNo);
            }
            if (DISPATCH_DISPATCHED.equals(wo.dispatchStatus)) {
                continue;
            }
            wo.dispatchStatus = DISPATCH_DISPATCHED;
            wo.dispatchedTs = now;
            dispatched.add(wo.workOrderNo);
        }
        if (dispatched.isEmpty()) {
            throw new BadRequestException("????????");
        }
        kittingService.computeForWorkOrders(dispatched);
        if (batchSplitConfig.mode() == BatchSplitMode.NONE) {
            for (String woNo : dispatched) {
                productionBatchSplitService.ensureDefaultBatchOnDispatch(woNo);
            }
        } else if (batchSplitConfig.autoOnDispatch()) {
            for (String woNo : dispatched) {
                try {
                    productionBatchSplitService.autoSplit(woNo);
                } catch (BadRequestException ignored) {
                    // 暂不可拆时跳过，不影响下发
                }
            }
        }
        return new WorkOrderDispatchResultDto(dispatched.size(), now, dispatched);
    }

    public OrderFulfillmentChainDto fulfillmentChain(String workOrderNo) {
        return fulfillmentChain(workOrderNo, null);
    }

    public OrderFulfillmentChainDto fulfillmentChain(String workOrderNo, String masterPlanVersionId) {
        return ontologyFulfillmentService.supplyOrderUpstreamChain(workOrderNo, masterPlanVersionId);
    }

    public OrderFulfillmentChainDto downstreamFulfillmentChain(String workOrderNo, String masterPlanVersionId) {
        return ontologyFulfillmentService.supplyOrderDownstreamChain(workOrderNo, masterPlanVersionId);
    }

    public List<WorkOrderPeggingDto> listPegging(String workOrderNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("?????: " + workOrderNo);
        }
        return WorkOrderPeggingEntity.findByWorkOrder(workOrderNo).stream()
                .map(p -> new WorkOrderPeggingDto(
                        p.id,
                        p.workOrderNo,
                        p.salesOrderNo,
                        p.salesOrderLineNo,
                        p.finishedProductCode,
                        p.peggedQty,
                        p.needDate))
                .toList();
    }

    private SalesOrderLineEntity resolvePrimaryOrder(WorkOrderEntity wo) {
        if (wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()) {
            return SalesOrderLineEntity.findByKey(wo.salesOrderNo, wo.salesOrderLineNo);
        }
        for (WorkOrderPeggingEntity peg : WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo)) {
            SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(peg.salesOrderNo, peg.salesOrderLineNo);
            if (order != null && !"CANCELLED".equals(order.status)) {
                return order;
            }
        }
        return null;
    }

    public List<WorkOrderKittingDto> kittingForDispatched() {
        return WorkOrderEntity.listInWorkspace().stream()
                .filter(wo -> DISPATCH_DISPATCHED.equals(normalizeDispatch(wo)))
                .sorted(Comparator.comparingInt(w -> w.sequenceNo))
                .map(this::toKittingDto)
                .toList();
    }

    @Transactional
    public List<WorkOrderKittingDto> recomputeDispatchedKitting() {
        List<String> nos = WorkOrderEntity.listInWorkspace().stream()
                .filter(wo -> DISPATCH_DISPATCHED.equals(normalizeDispatch(wo)))
                .map(wo -> wo.workOrderNo)
                .toList();
        kittingService.computeForWorkOrders(nos);
        return kittingForDispatched();
    }

    private WorkOrderKittingDto toKittingDto(WorkOrderEntity wo) {
        KittingResultEntity stored = KittingResultEntity
                .find("workspaceId = ?1 and workOrderNo = ?2 order by computedTs desc",
                        KittingResultEntity.ws(), wo.workOrderNo)
                .firstResult();
        WorkOrderKittingCheck check = computeKittingCheck(wo);
        String status = stored != null ? stored.kittingStatus : check.status();
        String reason = stored != null ? stored.shortageReason : check.reason();
        return new WorkOrderKittingDto(
                wo.workOrderNo,
                wo.productCode,
                wo.quantity,
                normalizeDispatch(wo),
                status,
                reason,
                check.lines());
    }

    private WorkOrderKittingCheck computeKittingCheck(WorkOrderEntity wo) {
        Map<String, BigDecimal> available = loadAvailableInventory();
        List<WorkOrderKittingLineDto> lines = new ArrayList<>();
        String status = "KITTING_OK";
        String reason = null;
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!bom.isCriticalComponent) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(wo.quantity);
            BigDecimal avail = available.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            boolean shortage = avail.compareTo(need) < 0;
            lines.add(new WorkOrderKittingLineDto(
                    bom.componentProductCode, need, avail, shortage));
            if (shortage && reason == null) {
                status = "SHORTAGE";
                reason = "??: " + bom.componentProductCode
                        + " ??=" + need + " ??=" + avail;
            }
            if (!shortage) {
                available.put(bom.componentProductCode, avail.subtract(need));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new WorkOrderKittingLineDto(
                    wo.productCode, wo.quantity,
                    available.getOrDefault(wo.productCode, BigDecimal.ZERO),
                    false));
        }
        return new WorkOrderKittingCheck(status, reason, lines);
    }

    private Map<String, BigDecimal> loadAvailableInventory() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
            map.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
        }
        return map;
    }

    private WorkOrderDto toDto(
            WorkOrderEntity wo,
            String masterPlanVersionId,
            String detailScheduleVersionId,
            Map<String, ScheduleFeedbackService.WorkOrderFeedbackFlags> feedbackFlags) {
        String source = wo.bomLevel == 0 ? "EXTERNAL" : "REPLENISH";
        int peggingCount = WorkOrderPeggingEntity.findByWorkOrder(wo.workOrderNo).size();
        String salesOrderNo = wo.salesOrderNo != null ? wo.salesOrderNo : "";
        LocalDate plannedSlotDate = null;
        String plannedShiftId = null;
        String plannedResourceId = wo.resourceId;
        boolean inScenarioPlan = false;
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            MasterPlanService.WorkOrderPlannedWindow window =
                    masterPlanService.resolveWorkOrderWindow(masterPlanVersionId, wo.workOrderNo);
            if (window != null) {
                inScenarioPlan = true;
                plannedSlotDate = window.slotDate();
                plannedShiftId = window.shiftId();
                if (window.resourceId() != null && !window.resourceId().isBlank()) {
                    plannedResourceId = window.resourceId();
                }
            }
        }
        ScheduleFeedbackService.WorkOrderFeedbackFlags flags = feedbackFlags.get(wo.workOrderNo);
        boolean hasFeedback = flags != null && flags.hasScheduleFeedback();
        boolean hasFrozen = flags != null && flags.hasFrozenScheduleFeedback();
        int fbCount = flags != null ? flags.operationCount() : 0;

        WorkOrderTimingWindowDto timingWindow = null;
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            timingWindow = workOrderTimingService.compute(wo.workOrderNo, masterPlanVersionId);
        }

        int routingOperationCount = ProductRoutingSteps.operationsForProduct(wo.productCode).size();
        int detailScheduledOperationCount = 0;
        if (detailScheduleVersionId != null && !detailScheduleVersionId.isBlank()) {
            detailScheduledOperationCount = DetailScheduleOperationEntity
                    .findByPlanAndWorkOrder(detailScheduleVersionId, wo.workOrderNo)
                    .size();
        }
        boolean detailScheduled = routingOperationCount > 0
                ? detailScheduledOperationCount >= routingOperationCount
                : detailScheduledOperationCount > 0;
        boolean pendingScheduleEligible = resolvePendingScheduleEligible(wo);

        return new WorkOrderDto(
                wo.id,
                wo.workOrderNo,
                wo.parentWorkOrderNo,
                source,
                salesOrderNo,
                wo.salesOrderLineNo,
                wo.productCode,
                wo.quantity,
                plannedResourceId,
                wo.sequenceNo,
                normalizeDispatch(wo),
                wo.dispatchedTs,
                plannedSlotDate,
                plannedShiftId,
                inScenarioPlan,
                hasFeedback,
                hasFrozen,
                fbCount,
                detailScheduleVersionId,
                wo.needDate,
                wo.bomLevel,
                peggingCount,
                timingWindow,
                pendingScheduleEligible,
                detailScheduled,
                routingOperationCount,
                detailScheduledOperationCount);
    }

    public static boolean isPendingScheduleEligible(WorkOrderEntity wo) {
        return wo.pendingScheduleEligible == null || wo.pendingScheduleEligible;
    }

    private static String normalizeDispatch(WorkOrderEntity wo) {
        if (wo.dispatchStatus == null || wo.dispatchStatus.isBlank()) {
            return DISPATCH_PENDING;
        }
        return wo.dispatchStatus;
    }

    private record WorkOrderKittingCheck(
            String status, String reason, List<WorkOrderKittingLineDto> lines) {
    }
}
