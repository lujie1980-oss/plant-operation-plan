package com.plantops.scenario;

import com.plantops.api.dto.BomRequirementDto;
import com.plantops.api.dto.DemandPoolEntryDto;
import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.DemandPoolSummaryDto;
import com.plantops.api.dto.DemandTrackingEntryDto;
import com.plantops.api.dto.DemandTrackingFlowStepDto;
import com.plantops.api.dto.DemandTrackingProcessEdgeDto;
import com.plantops.api.dto.DemandTrackingProcessNodeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DemandService {

    private static final LocalTime WORKDAY_START = LocalTime.of(8, 0);
    private static final LocalTime WORKDAY_END = LocalTime.of(17, 0);

    @Inject
    FulfillmentPeggingService fulfillmentPeggingService;

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    RuleScopeHelper ruleScopeHelper;

    public List<DemandPoolEntryDto> getDemandPool() {
        return getDemandPool(null);
    }

    public List<DemandPoolEntryDto> getDemandPool(String masterPlanVersionId) {
        return SalesOrderLineEntity.listInWorkspace().stream()
                .filter(o -> !"CANCELLED".equals(o.status))
                .sorted(Comparator.comparingInt((SalesOrderLineEntity o) -> o.priority)
                        .thenComparing(o -> o.dueDate))
                .map(o -> toDto(o, masterPlanVersionId))
                .toList();
    }

    @Transactional
    public int importOrders(List<DemandPoolEntryDto> entries) {
        int count = 0;
        for (DemandPoolEntryDto dto : entries) {
            SalesOrderLineEntity existing = SalesOrderLineEntity.findByKey(dto.salesOrderNo(), dto.salesOrderLineNo());
            SalesOrderLineEntity e = existing != null ? existing : new SalesOrderLineEntity();
            e.salesOrderNo = dto.salesOrderNo();
            e.salesOrderLineNo = dto.salesOrderLineNo();
            e.productCode = dto.productCode();
            e.orderQty = dto.orderQty();
            e.dueDate = dto.dueDate();
            e.promiseDate = dto.promiseDate();
            e.priority = dto.priority();
            e.expediteLevel = dto.expediteLevel();
            e.status = dto.status();
            e.scheduleLockFlag = dto.scheduleLockFlag();
            if (existing == null) {
                e.persist();
            }
            workOrderGenerationService.generateForOrderLine(
                    e.salesOrderNo, e.salesOrderLineNo, true);
            count++;
        }
        return count;
    }

    public DemandPoolSummaryDto getDemandPoolSummary() {
        return getDemandPoolSummary(null);
    }

    public DemandPoolSummaryDto getDemandPoolSummary(String masterPlanVersionId) {
        List<SalesOrderLineEntity> orders = activeOrders();
        LocalDate today = LocalDate.now();
        int shortage = 0;
        int kittingOk = 0;
        int dueSoon = 0;
        int overdue = 0;
        int expedite = 0;
        int locked = 0;
        double totalQty = 0;

        for (SalesOrderLineEntity o : orders) {
            String kitting = resolveKittingStatus(o.salesOrderNo, o.salesOrderLineNo);
            if ("SHORTAGE".equals(kitting)) {
                shortage++;
            } else {
                kittingOk++;
            }
            if (!o.dueDate.isBefore(today) && !o.dueDate.isAfter(today.plusDays(7))) {
                dueSoon++;
            }
            if (o.dueDate.isBefore(today)) {
                overdue++;
            }
            if (o.expediteLevel >= 2) {
                expedite++;
            }
            if (o.scheduleLockFlag) {
                locked++;
            }
            totalQty += o.orderQty.doubleValue();
        }

        List<DemandPoolKpiDto> kpis = List.of(
                kpi("TOTAL_LINES", "订单行数", orders.size(), "行", "info"),
                kpi("KITTING_OK", "齐套OK", kittingOk, "行", "ok"),
                kpi("SHORTAGE", "缺料行", shortage, "行", shortage > 0 ? "warn" : "ok"),
                kpi("DUE_7D", "7日内交期", dueSoon, "行", "info"),
                kpi("OVERDUE", "已逾期", overdue, "行", overdue > 0 ? "danger" : "ok"),
                kpi("EXPEDITE", "加急订单", expedite, "行", expedite > 0 ? "warn" : "ok"),
                kpi("LOCKED", "锁定订单", locked, "行", "info"),
                kpi("TOTAL_QTY", "总需求量", totalQty, "件", "info")
        );
        return new DemandPoolSummaryDto(kpis);
    }

    public List<DemandTrackingEntryDto> getDemandTracking() {
        return activeOrders().stream()
                .sorted(Comparator.comparingInt((SalesOrderLineEntity o) -> o.priority)
                        .thenComparing(o -> o.dueDate))
                .map(this::toTrackingDto)
                .toList();
    }

    public OrderFulfillmentChainDto getFulfillmentChain(String salesOrderNo, int salesOrderLineNo) {
        return getFulfillmentChain(salesOrderNo, salesOrderLineNo, null);
    }

    public OrderFulfillmentChainDto getFulfillmentChain(
            String salesOrderNo, int salesOrderLineNo, String masterPlanVersionId) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, salesOrderLineNo);
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("Sales order line not found: " + salesOrderNo + "-" + salesOrderLineNo);
        }
        String kittingStatus = resolveKittingStatus(salesOrderNo, salesOrderLineNo);
        return fulfillmentPeggingService.build(order, kittingStatus, masterPlanVersionId);
    }

    private List<SalesOrderLineEntity> activeOrders() {
        return SalesOrderLineEntity.listInWorkspace().stream()
                .filter(o -> !"CANCELLED".equals(o.status))
                .toList();
    }

    private DemandPoolKpiDto kpi(String id, String label, double value, String unit, String severity) {
        return new DemandPoolKpiDto(id, label, value, unit, severity);
    }

    public String resolveKittingStatusPublic(String salesOrderNo, int lineNo) {
        return resolveKittingStatus(salesOrderNo, lineNo);
    }

    private String resolveKittingStatus(String salesOrderNo, int lineNo) {
        KittingResultEntity r = KittingResultEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2 order by computedTs desc",
                        salesOrderNo, lineNo)
                .firstResult();
        if (r != null) {
            return r.kittingStatus;
        }
        return computeInlineKittingStatus(salesOrderNo, lineNo);
    }

    private String computeInlineKittingStatus(String salesOrderNo, int lineNo) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(salesOrderNo, lineNo);
        if (order == null) {
            return "UNKNOWN";
        }
        Map<String, BigDecimal> available = loadAvailableInventory();
        for (BomComponentEntity bom : BomComponentEntity.findChildren(order.productCode, order.productCode)) {
            if (!ruleScopeHelper.criticalForMasterPlan(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(order.orderQty);
            BigDecimal avail = available.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) {
                return "SHORTAGE";
            }
            available.put(bom.componentProductCode, avail.subtract(need));
        }
        return "KITTING_OK";
    }

    private Map<String, BigDecimal> loadAvailableInventory() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (InventoryEntity inv : InventoryEntity.listInWorkspace()) {
            map.merge(inv.productCode, inv.availableQty(), BigDecimal::add);
        }
        return map;
    }

    private MasterPlanAllocationEntity findLatestAllocation(String salesOrderNo, int lineNo) {
        return findAllocation(salesOrderNo, lineNo, null);
    }

    private MasterPlanAllocationEntity findAllocation(
            String salesOrderNo, int lineNo, String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            return MasterPlanAllocationEntity
                    .find(
                            "planVersionId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3 order by slotDate, slotIndex",
                            masterPlanVersionId,
                            salesOrderNo,
                            lineNo)
                    .firstResult();
        }
        List<MasterPlanAllocationEntity> list = MasterPlanAllocationEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2", salesOrderNo, lineNo)
                .<MasterPlanAllocationEntity>list();
        if (list.isEmpty()) {
            return null;
        }
        return list.stream()
                .max(Comparator
                        .comparing((MasterPlanAllocationEntity a) -> planVersionTs(a.planVersionId))
                        .thenComparing(a -> a.slotDate)
                        .thenComparingInt(a -> a.slotIndex))
                .orElse(null);
    }

    private LocalDateTime planVersionTs(String planVersionId) {
        return Optional.ofNullable(PlanVersionEntity.findByVersionId(planVersionId))
                .map(v -> v.planGeneratedTs)
                .orElse(LocalDateTime.MIN);
    }

    private List<DetailScheduleOperationEntity> findDetailOps(String workOrderNo) {
        if (workOrderNo == null || workOrderNo.isBlank()) {
            return List.of();
        }
        String latestPlan = DetailScheduleOperationEntity
                .find("workOrderNo = ?1", workOrderNo)
                .<DetailScheduleOperationEntity>list().stream()
                .map(o -> o.planVersionId)
                .max(Comparator.comparing(this::planVersionTs))
                .orElse(null);
        if (latestPlan == null) {
            return List.of();
        }
        return DetailScheduleOperationEntity
                .find("planVersionId = ?1 and workOrderNo = ?2 order by sequenceIndex", latestPlan, workOrderNo)
                .list();
    }

    private LocalDateTime shiftStart(LocalDate date, String shiftId) {
        int hour = "S2".equals(shiftId) || "NIGHT".equals(shiftId) ? 16 : 8;
        return date.atTime(hour, 0);
    }

    private String overallOrderStatus(
            String kitting,
            MasterPlanAllocationEntity allocation,
            List<DetailScheduleOperationEntity> detailOps) {
        if ("SHORTAGE".equals(kitting)) {
            return "AT_RISK";
        }
        if (!detailOps.isEmpty() && allocation != null) {
            return "ON_TRACK";
        }
        if (allocation != null) {
            return "PLANNED";
        }
        return "PENDING";
    }

    private DemandTrackingEntryDto toTrackingDto(SalesOrderLineEntity o) {
        String kitting = resolveKittingStatus(o.salesOrderNo, o.salesOrderLineNo);
        MasterPlanAllocationEntity alloc = findLatestAllocation(o.salesOrderNo, o.salesOrderLineNo);
        List<WorkOrderEntity> workOrders = WorkOrderEntity.findForOrderLine(o.salesOrderNo, o.salesOrderLineNo);
        int dispatched = (int) workOrders.stream()
                .filter(wo -> "DISPATCHED".equals(normalizeDispatchStatus(wo.dispatchStatus)))
                .count();
        int scheduledOps = 0;
        for (WorkOrderEntity wo : workOrders) {
            scheduledOps += findDetailOps(wo.workOrderNo).size();
        }
        String fulfillment = overallOrderStatus(kitting, alloc,
                workOrders.isEmpty() ? List.of() : findDetailOps(workOrders.get(0).workOrderNo));
        String execution = mapExecutionStatus(fulfillment, dispatched, workOrders.size(), scheduledOps);
        double progress = computeProgress(workOrders.size(), dispatched, scheduledOps);
        WorkOrderEntity rootWo = WorkOrderEntity.findRootForOrderLine(
                o.salesOrderNo, o.salesOrderLineNo, o.productCode);
        List<DetailScheduleOperationEntity> detailOps = rootWo != null
                ? findDetailOps(rootWo.workOrderNo)
                : List.of();
        List<DemandTrackingFlowStepDto> flowSteps = buildFlowSteps(
                o, kitting, fulfillment, workOrders.size(), dispatched, scheduledOps, execution, progress);
        ProcessPathGraph processGraph = buildProcessPath(
                o, kitting, alloc, rootWo, detailOps, fulfillment, dispatched, progress);
        return new DemandTrackingEntryDto(
                o.salesOrderNo,
                o.salesOrderLineNo,
                o.customerCode,
                o.productCode,
                o.orderQty,
                o.dueDate,
                o.promiseDate,
                o.priority,
                o.status,
                fulfillment,
                kitting,
                workOrders.size(),
                dispatched,
                scheduledOps,
                execution,
                progress,
                flowSteps,
                processGraph.nodes(),
                processGraph.edges());
    }

    private record ProcessPathGraph(
            List<DemandTrackingProcessNodeDto> nodes,
            List<DemandTrackingProcessEdgeDto> edges) {
    }

    private record RoutingStep(
            int sequenceNo, String operationName, String resourceId, BigDecimal processTimeSeconds) {
    }

    private List<DemandTrackingFlowStepDto> buildFlowSteps(
            SalesOrderLineEntity order,
            String kitting,
            String fulfillment,
            int woCount,
            int dispatched,
            int scheduledOps,
            String execution,
            double progress) {
        List<DemandTrackingFlowStepDto> steps = new ArrayList<>();
        steps.add(flowStep("order", "订单确认", "done", order.status));
        steps.add(flowStep(
                "fulfillment",
                "需求满足",
                flowStatusFromFulfillment(fulfillment),
                fulfillment));
        steps.add(flowStep(
                "kitting",
                "齐套检查",
                "SHORTAGE".equals(kitting) ? "risk" : "KITTING_OK".equals(kitting) ? "done" : "pending",
                kitting));
        steps.add(flowStep(
                "wo-gen",
                "工单生成",
                woCount > 0 ? "done" : "pending",
                woCount + " 张工单"));
        steps.add(flowStep(
                "wo-dispatch",
                "工单下发",
                dispatched > 0
                        ? (dispatched >= woCount && woCount > 0 ? "done" : "active")
                        : "pending",
                dispatched + "/" + woCount));
        steps.add(flowStep(
                "schedule",
                "详细排程",
                scheduledOps > 0 ? "done" : dispatched > 0 ? "active" : "pending",
                scheduledOps + " 道工序"));
        steps.add(flowStep(
                "delivery",
                "交付完成",
                progress >= 100 ? "done" : "IN_PRODUCTION".equals(execution) ? "active" : "pending",
                Math.round(progress) + "%"));
        linkFlowSteps(steps);
        return steps;
    }

    private void linkFlowSteps(List<DemandTrackingFlowStepDto> steps) {
        // status already encodes progression; edges rendered on frontend sequentially
    }

    private static DemandTrackingFlowStepDto flowStep(String id, String label, String status, String detail) {
        return new DemandTrackingFlowStepDto(id, label, status, detail);
    }

    private static String flowStatusFromFulfillment(String fulfillment) {
        return switch (fulfillment) {
            case "ON_TRACK", "PLANNED" -> "done";
            case "AT_RISK", "SHORTAGE" -> "risk";
            default -> "pending";
        };
    }

    private ProcessPathGraph buildProcessPath(
            SalesOrderLineEntity order,
            String kitting,
            MasterPlanAllocationEntity alloc,
            WorkOrderEntity rootWo,
            List<DetailScheduleOperationEntity> detailOps,
            String fulfillment,
            int dispatched,
            double progress) {
        List<DemandTrackingProcessNodeDto> nodes = new ArrayList<>();
        List<DemandTrackingProcessEdgeDto> edges = new ArrayList<>();
        int seq = 0;

        List<BomComponentEntity> criticalBoms = BomComponentEntity.findChildren(order.productCode, order.productCode).stream()
                .filter(b -> b.isCriticalComponent)
                .toList();
        if (criticalBoms.isEmpty()) {
            nodes.add(materialNode(
                    order,
                    "mat-all",
                    "成品",
                    order.productCode,
                    kitting,
                    dispatched,
                    seq++,
                    order.dueDate.minusDays(3)));
        } else {
            int lead = criticalBoms.size();
            for (BomComponentEntity bom : criticalBoms) {
                nodes.add(materialNode(
                        order,
                        "mat-" + bom.componentProductCode,
                        "组件 · " + bom.componentProductCode,
                        bom.componentProductCode,
                        kitting,
                        dispatched,
                        seq++,
                        order.dueDate.minusDays(lead--)));
            }
        }

        LocalDateTime woPlannedStart = null;
        LocalDateTime woPlannedEnd = null;
        if (alloc != null) {
            woPlannedStart = shiftStart(alloc.slotDate, alloc.shiftId);
            int duration = rootWo != null ? workOrderMinutes(rootWo) : 480;
            woPlannedEnd = woPlannedStart.plusMinutes(Math.max(1, duration));
        } else if (rootWo != null) {
            woPlannedEnd = order.dueDate.atTime(WORKDAY_END);
            woPlannedStart = woPlannedEnd.minusMinutes(workOrderMinutes(rootWo));
        }

        List<RoutingStep> routing = routingStepsFor(order.productCode);
        LocalDateTime cursor = woPlannedStart;
        long totalProcessSeconds = routing.stream()
                .mapToLong(s -> s.processTimeSeconds != null ? s.processTimeSeconds.longValue() : 0)
                .sum();
        long totalMinutes = woPlannedStart != null && woPlannedEnd != null
                ? java.time.Duration.between(woPlannedStart, woPlannedEnd).toMinutes()
                : 480;

        DetailScheduleOperationEntity detailOp = detailOps.isEmpty() ? null : detailOps.get(0);
        LocalDateTime detailProdStart = detailOp != null ? minuteToDateTime(detailOp.startMinute) : null;
        LocalDateTime detailProdEnd = detailOp != null ? minuteToDateTime(detailOp.endMinute) : null;

        for (int i = 0; i < routing.size(); i++) {
            RoutingStep step = routing.get(i);
            LocalDateTime planStart = cursor;
            LocalDateTime planEnd;
            if (woPlannedEnd != null && i == routing.size() - 1) {
                planEnd = woPlannedEnd;
            } else if (totalProcessSeconds > 0 && step.processTimeSeconds != null) {
                long share = step.processTimeSeconds.longValue();
                long minutes = Math.max(30, Math.round((double) totalMinutes * share / totalProcessSeconds));
                planEnd = planStart != null ? planStart.plusMinutes(minutes) : null;
                if (planEnd != null && woPlannedEnd != null && planEnd.isAfter(woPlannedEnd)) {
                    planEnd = woPlannedEnd;
                }
            } else if (planStart != null) {
                long perOp = Math.max(30, totalMinutes / Math.max(1, routing.size()));
                planEnd = planStart.plusMinutes(perOp);
            } else {
                planEnd = order.dueDate.atTime(WORKDAY_END);
                planStart = planEnd.minusHours(2);
            }

            boolean matchesDetail = detailOp != null
                    && rootWo != null
                    && step.resourceId.equals(rootWo.resourceId);
            LocalDateTime prodStart = matchesDetail ? detailProdStart : null;
            LocalDateTime prodEnd = matchesDetail ? detailProdEnd : null;

            String planStatus = resolveOperationPlanStatus(alloc, detailOp, matchesDetail, prodEnd);
            nodes.add(new DemandTrackingProcessNodeDto(
                    "op-" + step.sequenceNo,
                    "OPERATION",
                    step.operationName,
                    planStatus,
                    planStart,
                    planEnd,
                    prodStart,
                    prodEnd,
                    seq++));
            cursor = planEnd;
        }

        LocalDateTime orderPlanEnd = order.promiseDate != null && !order.promiseDate.isAfter(order.dueDate)
                ? order.promiseDate.atTime(WORKDAY_END)
                : order.dueDate.atTime(WORKDAY_END);
        LocalDateTime orderPlanStart = woPlannedStart != null ? woPlannedStart : orderPlanEnd.minusDays(7);
        String orderStatus = resolveOrderPlanStatus(fulfillment, progress);
        nodes.add(new DemandTrackingProcessNodeDto(
                "order-" + order.salesOrderNo + "-" + order.salesOrderLineNo,
                "ORDER",
                "订单 " + order.salesOrderNo + "-" + order.salesOrderLineNo,
                orderStatus,
                orderPlanStart,
                orderPlanEnd,
                detailProdStart,
                progress >= 100 ? orderPlanEnd : detailProdEnd,
                seq));

        for (int i = 0; i < nodes.size() - 1; i++) {
            edges.add(new DemandTrackingProcessEdgeDto(
                    nodes.get(i).nodeId(),
                    nodes.get(i + 1).nodeId()));
        }
        return new ProcessPathGraph(nodes, edges);
    }

    private DemandTrackingProcessNodeDto materialNode(
            SalesOrderLineEntity order,
            String nodeId,
            String label,
            String productCode,
            String kitting,
            int dispatched,
            int sequenceNo,
            LocalDate needDate) {
        LocalDateTime planEnd = needDate.atTime(WORKDAY_END);
        LocalDateTime planStart = planEnd.minusDays(1).with(WORKDAY_START);
        String status;
        if ("SHORTAGE".equals(kitting)) {
            status = "UNPLANNED";
        } else if (dispatched > 0) {
            status = "COMPLETED";
        } else if ("KITTING_OK".equals(kitting)) {
            status = "PLANNED";
        } else {
            status = "UNPLANNED";
        }
        LocalDateTime prodStart = dispatched > 0 ? planStart : null;
        LocalDateTime prodEnd = dispatched > 0 ? planEnd : null;
        return new DemandTrackingProcessNodeDto(
                nodeId,
                "RAW_MATERIAL",
                label,
                status,
                planStart,
                planEnd,
                prodStart,
                prodEnd,
                sequenceNo);
    }

    private static String resolveOperationPlanStatus(
            MasterPlanAllocationEntity alloc,
            DetailScheduleOperationEntity detailOp,
            boolean matchesDetail,
            LocalDateTime prodEnd) {
        if (matchesDetail && prodEnd != null && LocalDateTime.now().isAfter(prodEnd)) {
            return "COMPLETED";
        }
        if (detailOp != null && matchesDetail) {
            return "PLANNED";
        }
        if (alloc != null) {
            return "PLANNED";
        }
        return "UNPLANNED";
    }

    private static String resolveOrderPlanStatus(String fulfillment, double progress) {
        if (progress >= 100) {
            return "COMPLETED";
        }
        if ("ON_TRACK".equals(fulfillment) || "PLANNED".equals(fulfillment)) {
            return "PLANNED";
        }
        return "UNPLANNED";
    }

    private List<RoutingStep> routingStepsFor(String productCode) {
        List<ProductResourceEntity> rows = ProductResourceEntity.findByProductOrdered(productCode);
        if (rows.isEmpty()) {
            List<ProductRoutingCatalog.RoutingStep> fallback = ProductRoutingCatalog.stepsFor(productCode);
            List<RoutingStep> out = new ArrayList<>(fallback.size());
            for (int i = 0; i < fallback.size(); i++) {
                ProductRoutingCatalog.RoutingStep s = fallback.get(i);
                out.add(new RoutingStep(i + 1, s.operationName(), s.resourceId(), null));
            }
            return out;
        }
        List<RoutingStep> out = new ArrayList<>(rows.size());
        int fallbackSeq = 1;
        for (ProductResourceEntity row : rows) {
            int seq = row.sequenceNo != null ? row.sequenceNo : fallbackSeq++;
            String name = row.operationName != null && !row.operationName.isBlank()
                    ? row.operationName
                    : "工序 " + seq;
            out.add(new RoutingStep(seq, name, row.resourceId, row.processTimeSeconds));
        }
        return out;
    }

    private int workOrderMinutes(WorkOrderEntity wo) {
        return ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
    }

    private LocalDateTime minuteToDateTime(int minute) {
        return LocalDate.now().atTime(WORKDAY_START).plusMinutes(minute);
    }

    private static String mapExecutionStatus(
            String fulfillment, int dispatched, int woCount, int scheduledOps) {
        if (scheduledOps > 0) {
            return "IN_PRODUCTION";
        }
        if (dispatched > 0) {
            return "DISPATCHED";
        }
        if (woCount > 0) {
            return "PLANNED";
        }
        return switch (fulfillment) {
            case "ON_TRACK", "PLANNED" -> "PLANNED";
            case "AT_RISK" -> "AT_RISK";
            default -> "PENDING";
        };
    }

    private static String normalizeDispatchStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PENDING";
        }
        return status;
    }

    private static double computeProgress(int woCount, int dispatched, int scheduledOps) {
        if (woCount <= 0) {
            return 0;
        }
        if (scheduledOps > 0) {
            return Math.min(100, 40 + (60.0 * dispatched / woCount));
        }
        if (dispatched > 0) {
            return Math.min(90, 20 + (70.0 * dispatched / woCount));
        }
        return 10;
    }

    private DemandPoolEntryDto toDto(SalesOrderLineEntity o) {
        return toDto(o, null);
    }

    private DemandPoolEntryDto toDto(SalesOrderLineEntity o, String masterPlanVersionId) {
        List<BomRequirementDto> bom = BomComponentEntity.findChildren(o.productCode, o.productCode).stream()
                .map(b -> new BomRequirementDto(
                        b.componentProductCode,
                        b.componentQty.multiply(o.orderQty),
                        b.isCriticalComponent))
                .toList();
        String kitting = resolveKittingStatus(o.salesOrderNo, o.salesOrderLineNo);
        MasterPlanAllocationEntity alloc = findAllocation(o.salesOrderNo, o.salesOrderLineNo, masterPlanVersionId);
        WorkOrderEntity wo = WorkOrderEntity.findRootForOrderLine(
                o.salesOrderNo, o.salesOrderLineNo, o.productCode);
        List<DetailScheduleOperationEntity> ops = findDetailOps(wo != null ? wo.workOrderNo : null);
        return new DemandPoolEntryDto(
                o.salesOrderNo,
                o.salesOrderLineNo,
                o.productCode,
                o.orderQty,
                o.dueDate,
                o.promiseDate,
                o.priority,
                o.expediteLevel,
                o.status,
                o.scheduleLockFlag,
                kitting,
                overallOrderStatus(kitting, alloc, ops),
                bom);
    }
}
