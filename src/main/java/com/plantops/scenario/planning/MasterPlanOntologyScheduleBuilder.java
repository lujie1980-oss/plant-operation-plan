package com.plantops.scenario.planning;

import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.config.ParameterRegistry;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.scenario.planning.diagnostics.MasterPlanPlanningDiagnosticsCollector;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ADR-08 PATH-ONT：从权威 {@link OntologyGraph} 投影 {@link MasterPlanSchedule}，
 * 替代 PATH-ENT 实体扫描链（ADR-08 · TODO-08）。
 */
@ApplicationScoped
public class MasterPlanOntologyScheduleBuilder {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyToMasterPlanScheduleMapper scheduleMapper;

    @Inject
    ParameterRegistry parameters;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    public MasterPlanSchedule buildSchedule(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay,
            String planVersionId,
            Set<String> scopedWorkOrderNos) {
        MasterPlanCapacityOverlay overlay = capacityOverlay != null
                ? capacityOverlay
                : MasterPlanCapacityOverlay.empty();
        OntologyGraph graph = loadGraph(planVersionId);
        MasterPlanSolveProfile profile = toSolveProfile(resolved, overlay, graph, planVersionId);
        boolean multiResource = parameters.getBoolean("master_plan_multi_resource_split", false);
        MasterPlanSchedule schedule;
        if (multiResource) {
            schedule = scheduleMapper.toScheduleWithResourceCapacity(
                    graph, profile, scopedWorkOrderNos != null ? scopedWorkOrderNos : Set.of());
        } else {
            schedule = scheduleMapper.toSchedule(graph, profile);
            if (scopedWorkOrderNos != null && !scopedWorkOrderNos.isEmpty()) {
                schedule = scopeSchedule(schedule, scopedWorkOrderNos);
            }
        }
        return schedule;
    }

    public MasterPlanPlanningContext buildPlanningContext(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay,
            MaterialPlanningContext materialPlanning,
            String planVersionId,
            Set<String> scopedWorkOrderNos) {
        MaterialPlanningContext material = materialPlanning != null
                ? materialPlanning
                : materialPlanningContextBuilder.build();
        MasterPlanSchedule schedule = buildSchedule(resolved, capacityOverlay, planVersionId, scopedWorkOrderNos);
        return MasterPlanPlanningContext.fromSchedule(
                schedule,
                diagnosticsFrom(schedule, resolved, capacityOverlay, material),
                material);
    }

    public MasterPlanPlanningContext buildPlanningContext(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay,
            MaterialPlanningContext materialPlanning) {
        return buildPlanningContext(resolved, capacityOverlay, materialPlanning, null, null);
    }

    private OntologyGraph loadGraph(String planVersionId) {
        if (planVersionId == null || planVersionId.isBlank()) {
            return ontologyLoader.loadForWorkspace(LocalDate.now());
        }
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        return authoritativeOntologyGraph.getOrLoad(workspaceId, planVersionId.trim());
    }

    private static MasterPlanSolveProfile toSolveProfile(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay overlay,
            OntologyGraph graph,
            String planVersionId) {
        LocalDate planningStart;
        if (planVersionId != null && !planVersionId.isBlank()) {
            planningStart = graph.periodsOrdered().isEmpty()
                    ? LocalDate.now()
                    : graph.periodsOrdered().get(0).getStartDate();
        } else {
            planningStart = LocalDate.now();
        }
        return new MasterPlanSolveProfile(
                planningStart,
                resolved.capacityStrategy(),
                resolved.objectiveSettings(),
                overlay,
                resolved.id());
    }

    private static MasterPlanSchedule scopeSchedule(MasterPlanSchedule schedule, Set<String> scopedWorkOrderNos) {
        Set<String> scope = new LinkedHashSet<>(scopedWorkOrderNos);
        List<OrderAllocation> scopedAllocations = schedule.getOrderAllocations().stream()
                .filter(a -> scope.contains(a.getWorkOrderNo()))
                .toList();
        List<BomDependencyEdge> scopedBom = schedule.getBomDependencyEdges().stream()
                .filter(e -> scope.contains(e.parentWorkOrderNo()) && scope.contains(e.childWorkOrderNo()))
                .toList();
        List<OperationPrecedenceEdge> scopedPrecedence =
                MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(scopedAllocations);
        MasterPlanSchedule scoped = new MasterPlanSchedule(
                schedule.getTimeSlotRange(),
                scopedAllocations,
                schedule.getPlanningStart(),
                schedule.getPlanningSettings(),
                schedule.getMaterialFeasibility(),
                schedule.getObjectiveSettings(),
                schedule.getAdjacentSlotPairs(),
                schedule.getCapacityOverlay(),
                scopedBom,
                scopedPrecedence,
                schedule.getWorkOrderTimingBounds(),
                schedule.getChangeoverRuleIndex());
        if (schedule.hasResourceCapacityAssignments()) {
            scoped.setResourceCapacityAssignments(schedule.getResourceCapacityAssignments().stream()
                    .filter(a -> scope.contains(a.getWorkOrderNo()))
                    .toList());
            scoped.setOperationPrecedenceFacts(schedule.getOperationPrecedenceFacts());
        }
        return scoped;
    }

    private static com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto diagnosticsFrom(
            MasterPlanSchedule schedule,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay overlay,
            MaterialPlanningContext material) {
        MasterPlanPlanningDiagnosticsCollector diag = new MasterPlanPlanningDiagnosticsCollector();
        diag.set(PlanningDiagnosticCodes.MP_TIME_SLOT_COUNT, schedule.getTimeSlotRange().size());
        diag.set(
                PlanningDiagnosticCodes.MP_BOM_DEPENDENCY_EDGE_COUNT,
                schedule.getBomDependencyEdges() != null ? schedule.getBomDependencyEdges().size() : 0);
        diag.set(
                PlanningDiagnosticCodes.MP_OPERATION_PRECEDENCE_EDGES,
                schedule.getOperationPrecedenceEdges() != null ? schedule.getOperationPrecedenceEdges().size() : 0);
        int candidateCount = schedule.hasResourceCapacityAssignments()
                ? schedule.getResourceCapacityAssignments().size()
                : schedule.getOrderAllocations().size();
        diag.set(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_CANDIDATE, candidateCount);
        diag.set(PlanningDiagnosticCodes.MP_ORDER_ALLOCATIONS_REPLANNABLE, candidateCount);
        if (material != null && material.inventory() != null) {
            diag.set(PlanningDiagnosticCodes.MP_INVENTORY_PRODUCT_COUNT, material.inventory().productCount());
        }
        MasterPlanCapacityOverlay effectiveOverlay = overlay != null ? overlay : MasterPlanCapacityOverlay.empty();
        return diag.toDto(
                resolved.capacityStrategy(),
                effectiveOverlay.hasCutoff(),
                material != null ? material.inventorySnapshotId() : null);
    }
}
