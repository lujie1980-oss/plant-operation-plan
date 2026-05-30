package com.plantops.scenario.planning;

import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.MasterPlanParallelBindingResult;
import com.plantops.scenario.MasterPlanParallelBindingService;
import com.plantops.scenario.MaterialFeasibilityService;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.ScheduleFeedbackService;
import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.scenario.WorkOrderTimingService;
import com.plantops.scenario.planning.diagnostics.MasterPlanPlanningDiagnosticsCollector;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主计划推演层入口（P0–P4）：
 * <ul>
 *   <li>P0 事实装载：时隙、MRP 物料可行性、BOM 依赖、最早可行下界</li>
 *   <li>P1 工单筛选：可排程、冻结窗口、工艺路由非空</li>
 *   <li>P2 工序展开：{@link MasterPlanAllocationBuilder} 生成 OrderAllocation（含拆段）</li>
 *   <li>P3 可行域：按资源 + 反馈 overlay + 最早可行槽位过滤 eligibleTimeSlots</li>
 *   <li>P4 输出：{@link MasterPlanPlanningContext}（交由 {@link MasterPlanProblemMapper} 投影 Timefold）</li>
 * </ul>
 */
@ApplicationScoped
public class MasterPlanPlanningContextBuilder {

    @Inject
    ParameterRegistry parameters;

    @Inject
    MaterialFeasibilityService materialFeasibilityService;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    WorkOrderTimingService workOrderTimingService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    @Inject
    MasterPlanParallelBindingService masterPlanParallelBindingService;

    public MasterPlanPlanningContext build(
            MasterPlanCapacityStrategy strategy,
            MasterPlanObjectiveSettings objectiveSettings,
            MasterPlanCapacityOverlay capacityOverlay) {
        return build(strategy, objectiveSettings, capacityOverlay, null);
    }

    public MasterPlanPlanningContext build(
            MasterPlanCapacityStrategy strategy,
            MasterPlanObjectiveSettings objectiveSettings,
            MasterPlanCapacityOverlay capacityOverlay,
            MaterialPlanningContext materialPlanning) {
        MasterPlanPlanningDiagnosticsCollector diag = new MasterPlanPlanningDiagnosticsCollector();
        LocalDate planningStart = LocalDate.now();
        int freezeDays = parameters.getInt("freeze_window_days", 2);
        MasterPlanCapacityOverlay overlay = capacityOverlay != null
                ? capacityOverlay
                : MasterPlanCapacityOverlay.empty();
        MasterPlanCapacityStrategy effectiveStrategy = strategy != null
                ? strategy
                : MasterPlanCapacityStrategy.UNCONSTRAINED;
        MasterPlanObjectiveSettings effectiveObjectives = objectiveSettings != null
                ? objectiveSettings
                : new MasterPlanObjectiveSettings();

        List<TimeSlot> slots = timeslotHorizonService.buildSlots(
                planningStart, ProductionResourceEntity.routingResourceIds());
        MaterialPlanningContext effectiveMaterial = materialPlanning != null
                ? materialPlanning
                : materialPlanningContextBuilder.build();
        MaterialFeasibilityContext materialFeasibility = materialFeasibilityService.prepareContext(
                effectiveMaterial.inventory());
        List<BomDependencyEdge> bomEdges = loadBomDependencyEdges();
        WorkOrderTimingBoundsContext timingBounds = workOrderTimingService.buildMasterPlanBounds();

        diag.set(PlanningDiagnosticCodes.MP_TIME_SLOT_COUNT, slots.size());
        diag.set(PlanningDiagnosticCodes.MP_BOM_DEPENDENCY_EDGE_COUNT, bomEdges.size());
        diag.set(PlanningDiagnosticCodes.MP_INVENTORY_PRODUCT_COUNT, effectiveMaterial.inventory().productCount());

        List<OrderAllocation> candidates = new ArrayList<>();
        for (WorkOrderEntity wo : WorkOrderEntity.listAllOrdered()) {
            diag.increment(PlanningDiagnosticCodes.MP_WORK_ORDERS_SCANNED);
            WorkOrderScheduleContext scheduleCtx = WorkOrderScheduleContext.resolve(wo);
            if (!scheduleCtx.schedulable) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_NOT_SCHEDULABLE,
                        wo.workOrderNo,
                        null,
                        "工单不可排程（订单取消或未解析到有效交期）");
                continue;
            }
            if (overlay.hasCutoff()
                    && scheduleFeedbackService.isWorkOrderFrozenThroughCutoff(
                            wo.workOrderNo, overlay.feedbackCutoff())) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_FROZEN_THROUGH_CUTOFF,
                        wo.workOrderNo,
                        null,
                        "反馈截止日 " + overlay.feedbackCutoff() + " 前已冻结");
                continue;
            }
            List<ProductRoutingSteps.Operation> operations = ProductRoutingSteps.operationsForProduct(wo.productCode);
            if (operations.isEmpty()) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_NO_ROUTING,
                        wo.workOrderNo,
                        null,
                        "产品 " + wo.productCode + " 无 product_resource 工艺");
                continue;
            }
            boolean demandRules = businessRuleScopeService.isMasterPlanEnabled(
                    BusinessRuleTypeIds.DEMAND_PRIORITY_RULES);
            boolean locked = (demandRules && scheduleCtx.anyOrderLocked)
                    || scheduleCtx.dueDate.isBefore(planningStart.plusDays(freezeDays));
            List<OrderAllocation> woAllocations = MasterPlanAllocationBuilder.buildForWorkOrder(
                    wo,
                    scheduleCtx,
                    operations,
                    slots,
                    effectiveStrategy.isCapacityConstrained(),
                    locked,
                    businessRuleScopeService);
            if (woAllocations.isEmpty()) {
                diag.recordSkip(
                        PlanningDiagnosticCodes.WO_NO_ALLOCATIONS,
                        wo.workOrderNo,
                        null,
                        "工艺步骤均未解析到有效 resourceId");
                continue;
            }
            diag.increment(PlanningDiagnosticCodes.MP_WORK_ORDERS_WITH_ALLOCATIONS);
            candidates.addAll(woAllocations);
        }
        diag.set(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_CANDIDATE, candidates.size());

        List<OrderAllocation> replannable = applyEligibleTimeSlots(candidates, slots, overlay, timingBounds, diag);
        diag.set(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE, replannable.size());

        MasterPlanParallelBindingResult parallelResult = masterPlanParallelBindingService.applyBindings(
                replannable, slots, overlay, timingBounds, diag);
        diag.set(PlanningDiagnosticCodes.MP_PARALLEL_GROUPS, parallelResult.pairedGroups());
        diag.set(PlanningDiagnosticCodes.MP_PARALLEL_ORPHANS, parallelResult.orphans());
        diag.set(PlanningDiagnosticCodes.MP_PARALLEL_SLOT_INTERSECTIONS, parallelResult.slotIntersectionsApplied());
        diag.set(PlanningDiagnosticCodes.MP_PARALLEL_SLOT_FALLBACKS, parallelResult.slotIntersectionFallbacks());
        List<OperationPrecedenceEdge> operationPrecedenceEdges =
                MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(replannable);
        diag.set(PlanningDiagnosticCodes.MP_OPERATION_PRECEDENCE_EDGES, operationPrecedenceEdges.size());

        return new MasterPlanPlanningContext(
                planningStart,
                effectiveStrategy,
                effectiveObjectives,
                overlay,
                slots,
                replannable,
                materialFeasibility,
                bomEdges,
                operationPrecedenceEdges,
                timingBounds,
                diag.toDto(effectiveStrategy, overlay.hasCutoff(), effectiveMaterial.inventorySnapshotId()),
                effectiveMaterial);
    }

    private static List<OrderAllocation> applyEligibleTimeSlots(
            List<OrderAllocation> allocations,
            List<TimeSlot> slots,
            MasterPlanCapacityOverlay overlay,
            WorkOrderTimingBoundsContext timingBounds,
            MasterPlanPlanningDiagnosticsCollector diag) {
        List<OrderAllocation> replannable = new ArrayList<>();
        for (OrderAllocation allocation : allocations) {
            if (allocation.isLocked()) {
                diag.increment(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_LOCKED);
            }
            String rid = allocation.getResourceId();
            Set<String> eligibleResources = new LinkedHashSet<>();
            if (allocation.getAllowedResourceIds() != null && !allocation.getAllowedResourceIds().isEmpty()) {
                eligibleResources.addAll(allocation.getAllowedResourceIds());
            } else if (rid != null && !rid.isBlank()) {
                eligibleResources.add(rid);
            }
            List<TimeSlot> base = slots.stream()
                    .filter(s -> eligibleResources.contains(s.getResourceId()))
                    .filter(overlay::isSlotEligibleForReplan)
                    .toList();
            if (base.isEmpty()) {
                diag.increment(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_DROPPED_NO_SLOTS);
                diag.recordWarn(
                        PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS,
                        allocation.getWorkOrderNo(),
                        allocation.getId(),
                        "工序可选资源 " + eligibleResources + " 在规划时窗内无可用槽位（或被 overlay 占用）");
                continue;
            }
            List<TimeSlot> feasible = base.stream()
                    .filter(s -> timingBounds.slotAllowed(allocation.getWorkOrderNo(), s))
                    .toList();
            if (feasible.isEmpty()) {
                diag.increment(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_TIMING_FALLBACK);
                diag.recordWarn(
                        PlanningDiagnosticCodes.ALLOC_TIMING_FALLBACK,
                        allocation.getWorkOrderNo(),
                        allocation.getId(),
                        "无「不早于最早可行开始」槽位，回退全部 " + base.size() + " 个槽位并由软约束惩罚");
                allocation.setEligibleTimeSlots(base);
            } else {
                allocation.setEligibleTimeSlots(feasible);
            }
            replannable.add(allocation);
        }
        return replannable;
    }

    private static List<BomDependencyEdge> loadBomDependencyEdges() {
        List<BomDependencyEdge> edges = new ArrayList<>();
        for (WorkOrderBomDependencyEntity dep : WorkOrderBomDependencyEntity.listInWorkspace()) {
            edges.add(new BomDependencyEdge(dep.parentWorkOrderNo, dep.childWorkOrderNo));
        }
        return edges;
    }
}
