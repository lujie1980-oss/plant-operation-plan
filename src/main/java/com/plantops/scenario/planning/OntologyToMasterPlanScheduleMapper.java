package com.plantops.scenario.planning;

import com.plantops.config.MasterPlanPlanningSettingsFactory;
import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.fulfillment.BomDependencyDerivation;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.ontology.scheduling.PeriodTimeSlotAlignment;
import com.plantops.ontology.scheduling.SchedulingSlot;
import com.plantops.ontology.supply.OntologyRcaProjector;
import com.plantops.ontology.supply.OperationTimingBoundsProjection;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.MasterPlanParallelBindingService;
import com.plantops.scenario.ScheduleFeedbackService;
import com.plantops.scenario.WorkOrderScheduleContext;
import com.plantops.scenario.planning.diagnostics.MasterPlanPlanningDiagnosticsCollector;
import com.plantops.solver.masterplan.AdjacentSlotPairFactory;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 将 {@link OntologyGraph} 投影为 Timefold {@link MasterPlanSchedule}（路线 B 直驱入口）。
 */
@ApplicationScoped
public class OntologyToMasterPlanScheduleMapper {

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    MaterialFeasibilitySnapshotBuilder materialFeasibilitySnapshotBuilder;

    @Inject
    MasterPlanParallelBindingService masterPlanParallelBindingService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    ParameterRegistry parameters;

    @Inject
    MasterPlanPlanningSettingsFactory planningSettingsFactory;

    public MasterPlanSchedule toSchedule(OntologyGraph graph, MasterPlanSolveProfile profile) {
        if (graph == null) {
            return MasterPlanSchedule.empty();
        }
        MasterPlanSolveProfile effective = profile != null ? profile : MasterPlanSolveProfile.defaults(LocalDate.now());
        List<TimeSlot> slots = toTimeSlots(graph);
        PeriodTimeSlotAlignment.assertAligned(graph.schedulingSlotsOrdered(), slots);

        WorkOrderTimingBoundsContext timingBounds = OperationTimingBoundsProjection.fromGraph(graph);
        List<OrderAllocation> allocations = buildOrderAllocations(graph, effective, slots, timingBounds);

        MaterialFeasibilityContext materialFeasibility = materialFeasibilitySnapshotBuilder.toContext(graph);
        List<BomDependencyEdge> bomEdges = BomDependencyDerivation.toSolverEdges(graph);
        List<OperationPrecedenceEdge> precedenceEdges =
                MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(allocations);
        ChangeoverRuleIndex changeoverRules = businessRuleScopeService.loadMasterPlanChangeoverIndex();

        return new MasterPlanSchedule(
                slots,
                allocations,
                effective.planningStart(),
                planningSettingsFactory.create(effective.capacityStrategy()),
                materialFeasibility,
                effective.objectiveSettings(),
                AdjacentSlotPairFactory.fromSlots(slots),
                effective.capacityOverlay(),
                bomEdges,
                precedenceEdges,
                timingBounds,
                changeoverRules);
    }

    /**
     * 多机台拆分路径：展开 {@link ResourceCapacityAssignment} 供 OR-Tools CP-SAT 求解。
     */
    public MasterPlanSchedule toScheduleWithResourceCapacity(
            OntologyGraph graph,
            MasterPlanSolveProfile profile,
            Set<String> scopedWorkOrderNos) {
        if (graph == null) {
            return MasterPlanSchedule.empty();
        }
        MasterPlanSolveProfile effective = profile != null ? profile : MasterPlanSolveProfile.defaults(LocalDate.now());
        List<TimeSlot> slots = toTimeSlots(graph);
        PeriodTimeSlotAlignment.assertAligned(graph.schedulingSlotsOrdered(), slots);

        WorkOrderTimingBoundsContext timingBounds = OperationTimingBoundsProjection.fromGraph(graph);
        ResourceCapacityAssignmentBuilder.BuildResult built = buildResourceCapacityAssignments(
                graph, effective, slots, timingBounds, scopedWorkOrderNos);

        MaterialFeasibilityContext materialFeasibility = materialFeasibilitySnapshotBuilder.toContext(graph);
        List<BomDependencyEdge> bomEdges = BomDependencyDerivation.toSolverEdges(graph);
        ChangeoverRuleIndex changeoverRules = businessRuleScopeService.loadMasterPlanChangeoverIndex();

        MasterPlanSchedule schedule = new MasterPlanSchedule(
                slots,
                List.of(),
                effective.planningStart(),
                planningSettingsFactory.create(effective.capacityStrategy()),
                materialFeasibility,
                effective.objectiveSettings(),
                AdjacentSlotPairFactory.fromSlots(slots),
                effective.capacityOverlay(),
                bomEdges,
                List.of(),
                timingBounds,
                changeoverRules);
        schedule.setResourceCapacityAssignments(built.assignments());
        schedule.setOperationPrecedenceFacts(built.operationPrecedenceFacts());
        return schedule;
    }

    private ResourceCapacityAssignmentBuilder.BuildResult buildResourceCapacityAssignments(
            OntologyGraph graph,
            MasterPlanSolveProfile profile,
            List<TimeSlot> slots,
            WorkOrderTimingBoundsContext timingBounds,
            Set<String> scopedWorkOrderNos) {
        int freezeDays = parameters.getInt("freeze_window_days", 2);
        double demandScale = parameters.getDouble(
                "master_plan_demand_scale", MasterPlanDemandScaler.DEFAULT_SCALE);
        boolean demandRules = businessRuleScopeService.isMasterPlanEnabled(
                BusinessRuleTypeIds.DEMAND_PRIORITY_RULES);
        LocalDate freezeCutoff = profile.planningStart().plusDays(freezeDays);

        List<ResourceCapacityAssignment> candidates = new ArrayList<>();
        List<OperationPrecedenceFact> precedenceFacts = new ArrayList<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            if (scopedWorkOrderNos != null
                    && !scopedWorkOrderNos.isEmpty()
                    && !scopedWorkOrderNos.contains(supplyOrder.getId())) {
                continue;
            }
            WorkOrderEntity workOrder = WorkOrderEntity.findByNo(supplyOrder.getId());
            if (workOrder == null) {
                continue;
            }
            WorkOrderScheduleContext scheduleCtx = WorkOrderScheduleContext.resolve(workOrder);
            if (!scheduleCtx.schedulable) {
                continue;
            }
            if (profile.capacityOverlay().hasCutoff()
                    && scheduleFeedbackService.isWorkOrderFrozenThroughCutoff(
                            workOrder.workOrderNo, profile.capacityOverlay().feedbackCutoff())) {
                continue;
            }
            boolean locked = (demandRules && scheduleCtx.anyOrderLocked)
                    || (scheduleCtx.dueDate != null && scheduleCtx.dueDate.isBefore(freezeCutoff));
            int priority = demandRules ? scheduleCtx.priority : 5;
            ResourceCapacityAssignmentBuilder.BuildResult woResult =
                    ResourceCapacityAssignmentBuilder.buildForSupplyOrder(
                            graph,
                            supplyOrder,
                            slots,
                            profile.capacityStrategy().isCapacityConstrained(),
                            locked,
                            priority,
                            timingBounds,
                            profile.capacityOverlay(),
                            demandScale);
            ResourceCapacityAssignmentBuilder.enrichFromWorkOrder(
                    woResult.assignments(), workOrder, scheduleCtx);
            candidates.addAll(woResult.assignments());
            precedenceFacts.addAll(woResult.operationPrecedenceFacts());
        }
        OntologyRcaProjector.overlayOntologyOntoSolverCandidates(graph, candidates, slots);
        return new ResourceCapacityAssignmentBuilder.BuildResult(candidates, precedenceFacts);
    }

    private List<OrderAllocation> buildOrderAllocations(
            OntologyGraph graph,
            MasterPlanSolveProfile profile,
            List<TimeSlot> slots,
            WorkOrderTimingBoundsContext timingBounds) {
        int freezeDays = parameters.getInt("freeze_window_days", 2);
        double demandScale = parameters.getDouble(
                "master_plan_demand_scale", MasterPlanDemandScaler.DEFAULT_SCALE);
        boolean demandRules = businessRuleScopeService.isMasterPlanEnabled(
                BusinessRuleTypeIds.DEMAND_PRIORITY_RULES);
        LocalDate freezeCutoff = profile.planningStart().plusDays(freezeDays);

        List<OrderAllocation> candidates = new ArrayList<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            WorkOrderEntity workOrder = WorkOrderEntity.findByNo(supplyOrder.getId());
            if (workOrder == null) {
                continue;
            }
            WorkOrderScheduleContext scheduleCtx = WorkOrderScheduleContext.resolve(workOrder);
            if (!scheduleCtx.schedulable) {
                continue;
            }
            if (profile.capacityOverlay().hasCutoff()
                    && scheduleFeedbackService.isWorkOrderFrozenThroughCutoff(
                            workOrder.workOrderNo, profile.capacityOverlay().feedbackCutoff())) {
                continue;
            }
            boolean locked = (demandRules && scheduleCtx.anyOrderLocked)
                    || (scheduleCtx.dueDate != null && scheduleCtx.dueDate.isBefore(freezeCutoff));
            int priority = demandRules ? scheduleCtx.priority : 5;
            List<OrderAllocation> woAllocations = OntologyAllocationBuilder.buildForSupplyOrder(
                    graph,
                    supplyOrder,
                    slots,
                    profile.capacityStrategy().isCapacityConstrained(),
                    locked,
                    priority,
                    timingBounds,
                    profile.capacityOverlay(),
                    demandScale);
            enrichAllocations(woAllocations, workOrder, scheduleCtx);
            candidates.addAll(woAllocations);
        }

        MasterPlanPlanningDiagnosticsCollector diag = new MasterPlanPlanningDiagnosticsCollector();
        masterPlanParallelBindingService.applyBindings(
                candidates, slots, profile.capacityOverlay(), timingBounds, diag);
        return candidates;
    }

    private static void enrichAllocations(
            List<OrderAllocation> allocations,
            WorkOrderEntity workOrder,
            WorkOrderScheduleContext scheduleCtx) {
        for (OrderAllocation allocation : allocations) {
            allocation.setParentWorkOrderNo(workOrder.parentWorkOrderNo);
            allocation.setSalesOrderNo(scheduleCtx.salesOrderNo);
            allocation.setSalesOrderLineNo(scheduleCtx.salesOrderLineNo);
        }
    }

    private static List<TimeSlot> toTimeSlots(OntologyGraph graph) {
        return graph.schedulingSlotsOrdered().stream()
                .map(SchedulingSlot::toTimeSlot)
                .toList();
    }
}
