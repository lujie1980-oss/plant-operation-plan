package com.plantops.scenario;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderDispatchRequestDto;
import com.plantops.api.dto.WorkOrderDispatchResultDto;
import com.plantops.api.dto.WorkOrderDto;
import com.plantops.api.dto.WorkOrderKittingDto;
import com.plantops.api.dto.WorkOrderKittingLineDto;
import com.plantops.api.dto.WorkOrderScheduleOperationDto;
import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.api.dto.WorkOrderPeggingDto;
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
    MasterPlanService masterPlanService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    WorkOrderTimingService workOrderTimingService;

    public List<WorkOrderDto> listAll() {
        return listAll(null);
    }

    public List<WorkOrderDto> listAll(String masterPlanVersionId) {
        String detailScheduleVersionId = scheduleFeedbackService.resolveDetailScheduleVersionId(masterPlanVersionId);
        Map<String, ScheduleFeedbackService.WorkOrderFeedbackFlags> feedbackFlags =
                scheduleFeedbackService.feedbackFlagsByWorkOrder(detailScheduleVersionId);
        return WorkOrderEntity.listInWorkspace().stream()
                .sorted(Comparator
                        .comparingInt((WorkOrderEntity w) -> w.sequenceNo)
                        .thenComparing(w -> w.workOrderNo))
                .map(wo -> toDto(wo, masterPlanVersionId, detailScheduleVersionId, feedbackFlags))
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
        String detailScheduleVersionId = scheduleFeedbackService.resolveDetailScheduleVersionId(masterPlanVersionId);
        return scheduleFeedbackService.scheduleOperationsForWorkOrder(workOrderNo, detailScheduleVersionId);
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
        return new WorkOrderDispatchResultDto(dispatched.size(), now, dispatched);
    }

    public OrderFulfillmentChainDto fulfillmentChain(String workOrderNo) {
        return fulfillmentChain(workOrderNo, null);
    }

    public OrderFulfillmentChainDto fulfillmentChain(String workOrderNo, String masterPlanVersionId) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("?????: " + workOrderNo);
        }
        SalesOrderLineEntity order = resolvePrimaryOrder(wo);
        if (order == null) {
            throw new NotFoundException("工单无有效 pegging 或销售订单: " + workOrderNo);
        }
        WorkOrderKittingCheck check = computeKittingCheck(wo);
        return fulfillmentPeggingService.buildForWorkOrder(wo, order, check.status(), masterPlanVersionId);
    }

    public OrderFulfillmentChainDto downstreamFulfillmentChain(String workOrderNo, String masterPlanVersionId) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("?????: " + workOrderNo);
        }
        SalesOrderLineEntity order = resolvePrimaryOrder(wo);
        if (order == null) {
            throw new NotFoundException("工单无有效 pegging 或销售订单: " + workOrderNo);
        }
        WorkOrderKittingCheck check = computeKittingCheck(wo);
        return fulfillmentPeggingService.buildDownstreamForWorkOrder(
                wo, order, check.status(), masterPlanVersionId);
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
                timingWindow);
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
