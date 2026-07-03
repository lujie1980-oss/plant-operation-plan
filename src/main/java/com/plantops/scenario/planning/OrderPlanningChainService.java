package com.plantops.scenario.planning;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainPreviewRequest;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.KittingResultEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.DetailScheduleService;
import com.plantops.scenario.FulfillmentPeggingService;
import com.plantops.scenario.MasterPlanService;
import com.plantops.scenario.OntologyFulfillmentService;
import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.SlotFixedLoad;
import com.plantops.solver.masterplan.TimeSlot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
/**
 * @deprecated M5 Phase 2 — 单交付请用 {@link com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxService}。
 */
@Deprecated(since = "1.0", forRemoval = true)
public class OrderPlanningChainService {

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    FulfillmentPeggingService fulfillmentPeggingService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    MasterPlanProblemMapper problemMapper;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    public OrderPlanningChainDto preview(OrderPlanningChainPreviewRequest req) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(req.salesOrderNo(), req.salesOrderLineNo());
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("Sales order line not found: "
                    + req.salesOrderNo() + "-" + req.salesOrderLineNo());
        }

        sampleDataLoader.extendCalendarsToHorizon();

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                blankToNull(req.masterPlanStrategyId()));
        MasterPlanCapacityOverlay overlay = Boolean.TRUE.equals(req.useFeedbackOverlay())
                ? masterPlanService.buildFeedbackOverlay(
                        req.feedbackCutoff() != null ? req.feedbackCutoff() : LocalDate.now())
                : MasterPlanCapacityOverlay.empty();

        MaterialPlanningContext material = materialPlanningContextBuilder.build();
        MasterPlanPlanningContext mpCtx = masterPlanService.buildPlanningContext(resolved, overlay, material);

        DetailSchedulePlanningContext dsCtx = null;
        String detailMpId = blankToNull(req.detailScheduleMasterPlanVersionId());
        if (detailMpId != null) {
            dsCtx = detailScheduleService.buildPlanningContext(detailMpId, material);
        }

        String kittingStatus = resolveKittingStatus(order.salesOrderNo, order.salesOrderLineNo);
        OrderFulfillmentChainDto topology = fulfillmentPeggingService.build(order, kittingStatus, null);
        List<String> workOrderNos = extractWorkOrderNos(topology);

        String baselineId = blankToNull(req.baselineMasterPlanVersionId());
        OrderPlanningChainProjector.BaselineWindowResolver baseline = null;
        if (baselineId != null) {
            baseline = wo -> {
                var window = masterPlanService.resolveWorkOrderWindow(baselineId, wo);
                if (window == null) {
                    return null;
                }
                return new LocalDate[] {
                        window.plannedStart().toLocalDate(),
                        window.plannedEnd().toLocalDate()
                };
            };
        }

        return                 OrderPlanningChainProjector.project(
                topology, mpCtx, dsCtx, workOrderNos, baselineId, baseline);
    }

    /**
     * 单 {@code CustomerOrderLineDelivery} 有限能力：仅对本交付链工单 Timefold 求解，
     * 基线主计划其他工单以 fixed load 占用产能，不改动其排程结果。
     */
    public OrderPlanningChainDto previewFiniteForDelivery(OrderPlanningChainPreviewRequest req) {
        SalesOrderLineEntity order = SalesOrderLineEntity.findByKey(req.salesOrderNo(), req.salesOrderLineNo());
        if (order == null || "CANCELLED".equals(order.status)) {
            throw new NotFoundException("Sales order line not found: "
                    + req.salesOrderNo() + "-" + req.salesOrderLineNo());
        }

        String deliveryId = ontologyFulfillmentService.deliveryIdForOrderLine(
                req.salesOrderNo(), req.salesOrderLineNo());
        String baselineId = blankToNull(req.baselineMasterPlanVersionId());
        OrderFulfillmentChainDto topology = ontologyFulfillmentService.fulfillmentChain(deliveryId, baselineId);
        List<String> chainWorkOrderNos = extractChainWorkOrderNos(topology);
        if (chainWorkOrderNos.isEmpty()) {
            throw new BadRequestException("当前交付尚无 SupplyOrder，请先执行「无限能力计划（JIT）」");
        }

        sampleDataLoader.extendCalendarsToHorizon();
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                blankToNull(req.masterPlanStrategyId()));
        MaterialPlanningContext material = materialPlanningContextBuilder.build();
        LocalDate planningStart = LocalDate.now();
        List<TimeSlot> slots = timeslotHorizonService.buildSlots(
                planningStart, ProductionResourceEntity.routingResourceIds());
        Set<String> chainWoSet = new LinkedHashSet<>(chainWorkOrderNos);
        MasterPlanCapacityOverlay overlay = masterPlanService.buildBaselineOverlayExcludingWorkOrders(
                baselineId, chainWoSet, slots);
        if (Boolean.TRUE.equals(req.useFeedbackOverlay())) {
            LocalDate cutoff = req.feedbackCutoff() != null ? req.feedbackCutoff() : LocalDate.now();
            overlay = mergeOverlays(overlay, masterPlanService.buildFeedbackOverlay(cutoff));
        }

        MasterPlanPlanningContext fullCtx = masterPlanService.buildPlanningContext(resolved, overlay, material);
        List<OrderAllocation> scopedAllocations = fullCtx.orderAllocations().stream()
                .filter(a -> chainWoSet.contains(a.getWorkOrderNo()))
                .toList();
        if (scopedAllocations.isEmpty()) {
            throw new BadRequestException("交付链工单未进入可排程候选，请检查工艺路由与工单状态");
        }
        List<BomDependencyEdge> scopedBom = fullCtx.bomDependencyEdges().stream()
                .filter(e -> chainWoSet.contains(e.parentWorkOrderNo())
                        && chainWoSet.contains(e.childWorkOrderNo()))
                .toList();
        List<OperationPrecedenceEdge> scopedPrecedence =
                MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(scopedAllocations);
        MasterPlanPlanningContext scopedCtx = new MasterPlanPlanningContext(
                fullCtx.planningStart(),
                fullCtx.capacityStrategy(),
                fullCtx.objectiveSettings(),
                fullCtx.capacityOverlay(),
                fullCtx.timeSlots(),
                scopedAllocations,
                fullCtx.materialFeasibility(),
                scopedBom,
                scopedPrecedence,
                fullCtx.workOrderTimingBounds(),
                fullCtx.diagnostics(),
                fullCtx.materialPlanning());

        MasterPlanPlanningContext solvedCtx;
        try {
            MasterPlanService.InMemorySolveResult solveResult =
                    masterPlanService.solveInMemory(problemMapper.toSchedule(scopedCtx));
            solvedCtx = new MasterPlanPlanningContext(
                    scopedCtx.planningStart(),
                    scopedCtx.capacityStrategy(),
                    scopedCtx.objectiveSettings(),
                    scopedCtx.capacityOverlay(),
                    scopedCtx.timeSlots(),
                    solveResult.solution().getOrderAllocations(),
                    scopedCtx.materialFeasibility(),
                    scopedCtx.bomDependencyEdges(),
                    scopedCtx.operationPrecedenceEdges(),
                    scopedCtx.workOrderTimingBounds(),
                    scopedCtx.diagnostics(),
                    scopedCtx.materialPlanning());
        } catch (ExecutionException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("有限能力求解失败: " + ex.getMessage());
        }

        DetailSchedulePlanningContext dsCtx = null;
        String detailMpId = blankToNull(req.detailScheduleMasterPlanVersionId());
        if (detailMpId != null) {
            dsCtx = detailScheduleService.buildPlanningContext(detailMpId, material);
        }
        OrderPlanningChainProjector.BaselineWindowResolver baseline = null;
        if (baselineId != null) {
            baseline = wo -> {
                var window = masterPlanService.resolveWorkOrderWindow(baselineId, wo);
                if (window == null) {
                    return null;
                }
                return new LocalDate[] {
                        window.plannedStart().toLocalDate(),
                        window.plannedEnd().toLocalDate()
                };
            };
        }
        return OrderPlanningChainProjector.project(
                topology, solvedCtx, dsCtx, chainWorkOrderNos, baselineId, baseline);
    }

    private static MasterPlanCapacityOverlay mergeOverlays(
            MasterPlanCapacityOverlay baseline,
            MasterPlanCapacityOverlay feedback) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        if (baseline != null) {
            baseline.fixedMinutesBySlotId().forEach(merged::put);
        }
        if (feedback != null) {
            feedback.fixedMinutesBySlotId().forEach(
                    (slotId, minutes) -> merged.merge(slotId, minutes, Integer::sum));
        }
        LocalDate cutoff = feedback != null && feedback.hasCutoff()
                ? feedback.feedbackCutoff()
                : (baseline != null ? baseline.feedbackCutoff() : null);
        List<SlotFixedLoad> loads = new ArrayList<>();
        merged.forEach((slotId, minutes) -> loads.add(new SlotFixedLoad(slotId, minutes)));
        return MasterPlanCapacityOverlay.fromFixedLoads(loads, cutoff);
    }

    private static List<String> extractChainWorkOrderNos(OrderFulfillmentChainDto topology) {
        List<String> workOrderNos = new ArrayList<>();
        for (var node : topology.nodes()) {
            if ("SUPPLY_ORDER".equals(node.nodeType())) {
                workOrderNos.add(node.nodeId());
                continue;
            }
            if ("WORK_ORDER".equals(node.nodeType())) {
                Object wo = node.attributes() != null ? node.attributes().get("workOrderNo") : null;
                workOrderNos.add(wo != null ? wo.toString() : node.nodeId());
            }
        }
        return workOrderNos.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    }

    private static List<String> extractWorkOrderNos(OrderFulfillmentChainDto topology) {
        List<String> workOrderNos = new ArrayList<>();
        for (var node : topology.nodes()) {
            if (!"WORK_ORDER".equals(node.nodeType())) {
                continue;
            }
            Object wo = node.attributes() != null ? node.attributes().get("workOrderNo") : null;
            if (wo != null) {
                workOrderNos.add(wo.toString());
            }
        }
        return workOrderNos;
    }

    private static String resolveKittingStatus(String salesOrderNo, int lineNo) {
        KittingResultEntity result = KittingResultEntity
                .find("salesOrderNo = ?1 and salesOrderLineNo = ?2 order by computedTs desc",
                        salesOrderNo, lineNo)
                .firstResult();
        return result != null ? result.kittingStatus : "UNKNOWN";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
