package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MaterialFeasibilityContext;
import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;

import java.time.LocalDate;
import java.util.List;

/**
 * 主计划单次求解的推演快照（P0–P4）：事实 + 候选分配，不含 Timefold 得分。
 * Timefold 输入由 {@link OntologyToMasterPlanScheduleMapper} 投影（PATH-ONT · ADR-08）。
 */
public final class MasterPlanPlanningContext {

    private final LocalDate planningStart;
    private final MasterPlanCapacityStrategy capacityStrategy;
    private final MasterPlanObjectiveSettings objectiveSettings;
    private final MasterPlanCapacityOverlay capacityOverlay;
    private final List<TimeSlot> timeSlots;
    private final List<OrderAllocation> orderAllocations;
    private final MaterialFeasibilityContext materialFeasibility;
    private final List<BomDependencyEdge> bomDependencyEdges;
    private final List<OperationPrecedenceEdge> operationPrecedenceEdges;
    private final List<ResourceCapacityAssignment> resourceCapacityAssignments;
    private final List<OperationPrecedenceFact> operationPrecedenceFacts;
    private final boolean multiResourceSplit;
    private final WorkOrderTimingBoundsContext workOrderTimingBounds;
    private final MasterPlanPlanningDiagnosticsDto diagnostics;
    private final MaterialPlanningContext materialPlanning;

    public MasterPlanPlanningContext(
            LocalDate planningStart,
            MasterPlanCapacityStrategy capacityStrategy,
            MasterPlanObjectiveSettings objectiveSettings,
            MasterPlanCapacityOverlay capacityOverlay,
            List<TimeSlot> timeSlots,
            List<OrderAllocation> orderAllocations,
            MaterialFeasibilityContext materialFeasibility,
            List<BomDependencyEdge> bomDependencyEdges,
            List<OperationPrecedenceEdge> operationPrecedenceEdges,
            WorkOrderTimingBoundsContext workOrderTimingBounds,
            MasterPlanPlanningDiagnosticsDto diagnostics,
            MaterialPlanningContext materialPlanning) {
        this.planningStart = planningStart;
        this.capacityStrategy = capacityStrategy;
        this.objectiveSettings = objectiveSettings;
        this.capacityOverlay = capacityOverlay != null ? capacityOverlay : MasterPlanCapacityOverlay.empty();
        this.timeSlots = timeSlots != null ? List.copyOf(timeSlots) : List.of();
        this.orderAllocations = orderAllocations != null ? List.copyOf(orderAllocations) : List.of();
        this.materialFeasibility = materialFeasibility;
        this.bomDependencyEdges = bomDependencyEdges != null ? List.copyOf(bomDependencyEdges) : List.of();
        this.operationPrecedenceEdges = operationPrecedenceEdges != null
                ? List.copyOf(operationPrecedenceEdges)
                : List.of();
        this.resourceCapacityAssignments = List.of();
        this.operationPrecedenceFacts = List.of();
        this.multiResourceSplit = false;
        this.workOrderTimingBounds = workOrderTimingBounds != null
                ? workOrderTimingBounds
                : WorkOrderTimingBoundsContext.empty();
        this.diagnostics = diagnostics;
        this.materialPlanning = materialPlanning;
    }

    /** 兼容旧构造：未传入共享物料上下文时为空。 */
    public MasterPlanPlanningContext(
            LocalDate planningStart,
            MasterPlanCapacityStrategy capacityStrategy,
            MasterPlanObjectiveSettings objectiveSettings,
            MasterPlanCapacityOverlay capacityOverlay,
            List<TimeSlot> timeSlots,
            List<OrderAllocation> orderAllocations,
            MaterialFeasibilityContext materialFeasibility,
            List<BomDependencyEdge> bomDependencyEdges,
            WorkOrderTimingBoundsContext workOrderTimingBounds,
            MasterPlanPlanningDiagnosticsDto diagnostics) {
        this(
                planningStart,
                capacityStrategy,
                objectiveSettings,
                capacityOverlay,
                timeSlots,
                orderAllocations,
                materialFeasibility,
                bomDependencyEdges,
                List.of(),
                workOrderTimingBounds,
                diagnostics,
                null);
    }

    public MasterPlanPlanningContext(
            LocalDate planningStart,
            MasterPlanCapacityStrategy capacityStrategy,
            MasterPlanObjectiveSettings objectiveSettings,
            MasterPlanCapacityOverlay capacityOverlay,
            List<TimeSlot> timeSlots,
            List<OrderAllocation> orderAllocations,
            MaterialFeasibilityContext materialFeasibility,
            List<BomDependencyEdge> bomDependencyEdges,
            List<OperationPrecedenceEdge> operationPrecedenceEdges,
            WorkOrderTimingBoundsContext workOrderTimingBounds,
            MasterPlanPlanningDiagnosticsDto diagnostics,
            MaterialPlanningContext materialPlanning,
            List<ResourceCapacityAssignment> resourceCapacityAssignments,
            List<OperationPrecedenceFact> operationPrecedenceFacts,
            boolean multiResourceSplit) {
        this.planningStart = planningStart;
        this.capacityStrategy = capacityStrategy;
        this.objectiveSettings = objectiveSettings;
        this.capacityOverlay = capacityOverlay != null ? capacityOverlay : MasterPlanCapacityOverlay.empty();
        this.timeSlots = timeSlots != null ? List.copyOf(timeSlots) : List.of();
        this.orderAllocations = orderAllocations != null ? List.copyOf(orderAllocations) : List.of();
        this.materialFeasibility = materialFeasibility;
        this.bomDependencyEdges = bomDependencyEdges != null ? List.copyOf(bomDependencyEdges) : List.of();
        this.operationPrecedenceEdges = operationPrecedenceEdges != null
                ? List.copyOf(operationPrecedenceEdges)
                : List.of();
        this.resourceCapacityAssignments = resourceCapacityAssignments != null
                ? List.copyOf(resourceCapacityAssignments)
                : List.of();
        this.operationPrecedenceFacts = operationPrecedenceFacts != null
                ? List.copyOf(operationPrecedenceFacts)
                : List.of();
        this.multiResourceSplit = multiResourceSplit;
        this.workOrderTimingBounds = workOrderTimingBounds != null
                ? workOrderTimingBounds
                : WorkOrderTimingBoundsContext.empty();
        this.diagnostics = diagnostics;
        this.materialPlanning = materialPlanning;
    }

    public LocalDate planningStart() {
        return planningStart;
    }

    public MasterPlanCapacityStrategy capacityStrategy() {
        return capacityStrategy;
    }

    public MasterPlanObjectiveSettings objectiveSettings() {
        return objectiveSettings;
    }

    public MasterPlanCapacityOverlay capacityOverlay() {
        return capacityOverlay;
    }

    public List<TimeSlot> timeSlots() {
        return timeSlots;
    }

    public List<OrderAllocation> orderAllocations() {
        return orderAllocations;
    }

    public MaterialFeasibilityContext materialFeasibility() {
        return materialFeasibility;
    }

    public List<BomDependencyEdge> bomDependencyEdges() {
        return bomDependencyEdges;
    }

    public List<OperationPrecedenceEdge> operationPrecedenceEdges() {
        return operationPrecedenceEdges;
    }

    public List<ResourceCapacityAssignment> resourceCapacityAssignments() {
        return resourceCapacityAssignments;
    }

    public List<OperationPrecedenceFact> operationPrecedenceFacts() {
        return operationPrecedenceFacts;
    }

    public boolean multiResourceSplit() {
        return multiResourceSplit;
    }

    public boolean hasResourceCapacityAssignments() {
        return multiResourceSplit && !resourceCapacityAssignments.isEmpty();
    }

    public WorkOrderTimingBoundsContext workOrderTimingBounds() {
        return workOrderTimingBounds;
    }

    public MasterPlanPlanningDiagnosticsDto diagnostics() {
        return diagnostics;
    }

    public MaterialPlanningContext materialPlanning() {
        return materialPlanning;
    }

    /** PATH-ONT：由 {@link MasterPlanSchedule} 反建推演快照（ADR-08）。 */
    public static MasterPlanPlanningContext fromSchedule(
            MasterPlanSchedule schedule,
            MasterPlanPlanningDiagnosticsDto diagnostics,
            MaterialPlanningContext materialPlanning) {
        if (schedule == null) {
            return new MasterPlanPlanningContext(
                    LocalDate.now(),
                    com.plantops.solver.masterplan.MasterPlanCapacityStrategy.UNCONSTRAINED,
                    new com.plantops.solver.masterplan.MasterPlanObjectiveSettings(),
                    MasterPlanCapacityOverlay.empty(),
                    List.of(),
                    List.of(),
                    new MaterialFeasibilityContext(java.util.Map.of()),
                    List.of(),
                    List.of(),
                    WorkOrderTimingBoundsContext.empty(),
                    diagnostics,
                    materialPlanning);
        }
        com.plantops.solver.masterplan.MasterPlanCapacityStrategy capacityStrategy =
                schedule.getPlanningSettings() != null
                        ? schedule.getPlanningSettings().getCapacityStrategy()
                        : com.plantops.solver.masterplan.MasterPlanCapacityStrategy.UNCONSTRAINED;
        if (schedule.hasResourceCapacityAssignments()) {
            return new MasterPlanPlanningContext(
                    schedule.getPlanningStart(),
                    capacityStrategy,
                    schedule.getObjectiveSettings(),
                    schedule.getCapacityOverlay(),
                    schedule.getTimeSlotRange(),
                    List.of(),
                    schedule.getMaterialFeasibility(),
                    schedule.getBomDependencyEdges(),
                    schedule.getOperationPrecedenceEdges(),
                    schedule.getWorkOrderTimingBounds(),
                    diagnostics,
                    materialPlanning,
                    schedule.getResourceCapacityAssignments(),
                    schedule.getOperationPrecedenceFacts(),
                    true);
        }
        return new MasterPlanPlanningContext(
                schedule.getPlanningStart(),
                capacityStrategy,
                schedule.getObjectiveSettings(),
                schedule.getCapacityOverlay(),
                schedule.getTimeSlotRange(),
                schedule.getOrderAllocations(),
                schedule.getMaterialFeasibility(),
                schedule.getBomDependencyEdges(),
                schedule.getOperationPrecedenceEdges(),
                schedule.getWorkOrderTimingBounds(),
                diagnostics,
                materialPlanning);
    }
}
