package com.plantops.scenario.planning.delivery;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.config.ParameterRegistry;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.fulfillment.OntologyFulfillmentChainProjector;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.scenario.planning.MaterialPlanningContextBuilder;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.DemandService;
import com.plantops.scenario.MasterPlanService;
import com.plantops.scenario.OntologyFulfillmentService;
import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.scenario.planning.MasterPlanOperationPrecedenceBuilder;
import com.plantops.scenario.planning.MasterPlanPlanningContext;
import com.plantops.scenario.planning.MasterPlanProblemMapper;
import com.plantops.scenario.planning.MaterialPlanningContext;
import com.plantops.scenario.planning.OntologyToMasterPlanScheduleMapper;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerException;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerRegistry;
import com.plantops.scenario.planning.optimizer.PlanningProblem;
import com.plantops.scenario.planning.optimizer.PlanningResultApplicator;
import com.plantops.scenario.planning.optimizer.ortools.OrtoolsPlanningOptimizer;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.SlotFixedLoad;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class DeliveryPlanningSandboxService {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    DeliveryPlanningSandboxStore sandboxStore;

    @Inject
    OntologyFulfillmentChainProjector chainProjector;

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    DemandService demandService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    PlanningOptimizerRegistry optimizerRegistry;

    @Inject
    PlanningResultApplicator resultApplicator;

    @Inject
    ParameterRegistry parameters;

    @Inject
    OntologyToMasterPlanScheduleMapper ontologyToMasterPlanScheduleMapper;

    public DeliveryPlanningSandbox getOrCreate(String deliveryId, String baselinePlanVersionId) {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        String normalizedBaseline = blankToNull(baselinePlanVersionId);
        OntologyGraph graph = authoritativeOntologyGraph.getOrLoad(workspaceId, normalizedBaseline);
        DeliveryPlanningSandbox existing = sandboxStore.findByDelivery(workspaceId, deliveryId);
        if (existing != null) {
            if (existing.graph() != graph) {
                existing = rebindGraph(existing, graph);
                sandboxStore.put(existing);
            }
            return existing;
        }
        if (graph.customerOrderLineDelivery(deliveryId) == null) {
            throw new NotFoundException("Customer order line delivery not found: " + deliveryId);
        }
        LocalDateTime createdAt = LocalDateTime.now();
        DeliveryPlanningSandbox sandbox = new DeliveryPlanningSandbox(
                "DPS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                workspaceId,
                deliveryId,
                normalizedBaseline,
                graph,
                authoritativeOntologyGraph.newRolEngine(graph),
                createdAt,
                sandboxStore.defaultExpiresAt(createdAt));
        return sandboxStore.put(sandbox);
    }

    public OrderFulfillmentChainDto optimizeForDelivery(
            String salesOrderNo,
            int salesOrderLineNo,
            String masterPlanStrategyId,
            String baselinePlanVersionId,
            Boolean useFeedbackOverlay,
            LocalDate feedbackCutoff) {
        String deliveryId = ontologyFulfillmentService.deliveryIdForOrderLine(salesOrderNo, salesOrderLineNo);
        invalidateForDelivery(deliveryId);
        DeliveryPlanningSandbox sandbox = getOrCreate(deliveryId, baselinePlanVersionId);

        OrderFulfillmentChainDto topology = projectChain(sandbox);
        List<String> chainWorkOrderNos = extractChainWorkOrderNos(topology);
        if (chainWorkOrderNos.isEmpty()) {
            throw new BadRequestException("当前交付尚无 SupplyOrder，请先执行「无限能力计划（JIT）」");
        }

        sampleDataLoader.extendCalendarsToHorizon();
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                blankToNull(masterPlanStrategyId));
        MaterialPlanningContext material = materialPlanningContextBuilder.build();
        LocalDate planningStart = LocalDate.now();
        List<TimeSlot> slots = timeslotHorizonService.buildSlots(
                planningStart, ProductionResourceEntity.routingResourceIds());
        Set<String> chainWoSet = new LinkedHashSet<>(chainWorkOrderNos);
        String baselineId = blankToNull(baselinePlanVersionId);
        MasterPlanCapacityOverlay overlay = masterPlanService.buildBaselineOverlayExcludingWorkOrders(
                baselineId, chainWoSet, slots);
        if (Boolean.TRUE.equals(useFeedbackOverlay)) {
            LocalDate cutoff = feedbackCutoff != null ? feedbackCutoff : LocalDate.now();
            overlay = mergeOverlays(overlay, masterPlanService.buildFeedbackOverlay(cutoff));
        }

        MasterPlanPlanningContext fullCtx = masterPlanService.buildPlanningContext(resolved, overlay, material);
        MasterPlanPlanningContext scopedCtx = scopeContext(fullCtx, chainWoSet);
        if (!parameters.getBoolean("master_plan_multi_resource_split", false)
                && scopedCtx.orderAllocations().isEmpty()) {
            throw new BadRequestException("交付链工单未进入可排程候选，请检查工艺路由与工单状态");
        }

        PlanningProblem problem;
        if (parameters.getBoolean("master_plan_multi_resource_split", false)) {
            MasterPlanSolveProfile profile = new MasterPlanSolveProfile(
                    planningStart,
                    resolved.capacityStrategy(),
                    resolved.objectiveSettings(),
                    overlay,
                    resolved.id());
            MasterPlanSchedule ontologySchedule = ontologyToMasterPlanScheduleMapper.toScheduleWithResourceCapacity(
                    sandbox.graph(), profile, chainWoSet);
            if (!ontologySchedule.hasResourceCapacityAssignments()) {
                throw new BadRequestException("多机台拆分未生成可排程候选，请检查 OOSR 工艺绑定");
            }
            problem = PlanningProblem.forOntologySchedule(ontologySchedule, deliveryId);
        } else {
            problem = PlanningProblem.forContext(scopedCtx, deliveryId, chainWoSet);
        }

        var optimizer = parameters.getBoolean("master_plan_multi_resource_split", false)
                ? optimizerRegistry.require(OrtoolsPlanningOptimizer.ENGINE_ID)
                : optimizerRegistry.requireDefault();
        OptimizerResult optimizerResult;
        try {
            optimizerResult = optimizer.optimize(problem);
        } catch (PlanningOptimizerException ex) {
            throw new BadRequestException("有限能力求解失败: " + ex.getMessage());
        }

        OptimizerResult applied = resultApplicator.applyFromOptimizerResult(
                sandbox.graph(),
                sandbox.rolEngine(),
                optimizerResult,
                chainWoSet);

        DeliveryPlanningSandbox updated = sandbox.withTrialResult(sandbox.trialRevision() + 1, applied);
        sandboxStore.put(updated);
        return projectChain(updated, applied);
    }

    public OrderFulfillmentChainDto projectChain(DeliveryPlanningSandbox sandbox) {
        return projectChain(sandbox, sandbox.lastOptimizerResult());
    }

    public OrderFulfillmentChainDto projectChain(DeliveryPlanningSandbox sandbox, OptimizerResult optimizerResult) {
        OrderFulfillmentChainDto chain = chainProjector.project(
                sandbox.graph(),
                sandbox.deliveryId(),
                optimizerResult,
                sandbox.trialRevision());
        if (chain == null) {
            throw new NotFoundException("Fulfillment chain not available for delivery: " + sandbox.deliveryId());
        }
        var line = sandbox.graph().customerOrderLine(
                sandbox.graph().customerOrderLineDelivery(sandbox.deliveryId()).getCustomerOrderLineId());
        String kitting = line != null
                ? demandService.resolveKittingStatusPublic(line.getSalesOrderNo(), line.getSalesOrderLineNo())
                : chain.kittingStatus();
        return new OrderFulfillmentChainDto(
                chain.salesOrderNo(),
                chain.salesOrderLineNo(),
                chain.productCode(),
                chain.dueDate(),
                chain.promiseDate(),
                chain.overallStatus(),
                kitting,
                chain.nodes(),
                chain.edges(),
                chain.utilizationBuckets(),
                sandbox.deliveryId());
    }

    public void invalidateForDelivery(String deliveryId) {
        DeliveryPlanningSandbox sandbox = sandboxStore.findByDelivery(
                WorkspaceResolver.currentWorkspaceId(), deliveryId);
        if (sandbox != null) {
            sandboxStore.remove(sandbox.sandboxId());
        }
    }

    private static MasterPlanPlanningContext scopeContext(
            MasterPlanPlanningContext fullCtx,
            Set<String> chainWoSet) {
        List<OrderAllocation> scopedAllocations = fullCtx.orderAllocations().stream()
                .filter(a -> chainWoSet.contains(a.getWorkOrderNo()))
                .toList();
        List<BomDependencyEdge> scopedBom = fullCtx.bomDependencyEdges().stream()
                .filter(e -> chainWoSet.contains(e.parentWorkOrderNo())
                        && chainWoSet.contains(e.childWorkOrderNo()))
                .toList();
        List<OperationPrecedenceEdge> scopedPrecedence =
                MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(scopedAllocations);
        return new MasterPlanPlanningContext(
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
                Object wo = node.attributes() != null ? node.attributes().get("workOrderNo") : null;
                if (wo != null) {
                    workOrderNos.add(wo.toString());
                } else if (node.nodeId().startsWith("supo-")) {
                    workOrderNos.add(node.nodeId().substring("supo-".length()));
                } else {
                    workOrderNos.add(node.nodeId());
                }
                continue;
            }
            if ("WORK_ORDER".equals(node.nodeType())) {
                Object wo = node.attributes() != null ? node.attributes().get("workOrderNo") : null;
                workOrderNos.add(wo != null ? wo.toString() : node.nodeId());
            }
        }
        return workOrderNos.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    }

    private DeliveryPlanningSandbox rebindGraph(DeliveryPlanningSandbox sandbox, OntologyGraph graph) {
        return new DeliveryPlanningSandbox(
                sandbox.sandboxId(),
                sandbox.workspaceId(),
                sandbox.deliveryId(),
                sandbox.baselinePlanVersionId(),
                graph,
                authoritativeOntologyGraph.newRolEngine(graph),
                sandbox.createdAt(),
                sandbox.expiresAt(),
                sandbox.trialRevision(),
                sandbox.lastOptimizerResult());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
