package com.plantops.solver.masterplan;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@PlanningSolution
public class MasterPlanSchedule {

    @ProblemFactCollectionProperty
    private List<TimeSlot> timeSlotRange;

    @PlanningEntityCollectionProperty
    private List<OrderAllocation> orderAllocations;

    @PlanningScore
    private HardSoftScore score;

    @ProblemFactProperty
    private MasterPlanSettings planningSettings;

    @ProblemFactProperty
    private MaterialFeasibilityContext materialFeasibility;

    @ProblemFactProperty
    private MasterPlanObjectiveSettings objectiveSettings;

    @ProblemFactCollectionProperty
    private List<AdjacentSlotPair> adjacentSlotPairs;

    @ProblemFactProperty
    private MasterPlanCapacityOverlay capacityOverlay;

    @ProblemFactCollectionProperty
    private List<BomDependencyEdge> bomDependencyEdges;

    @ProblemFactCollectionProperty
    private List<OperationPrecedenceEdge> operationPrecedenceEdges;

    @ProblemFactProperty
    private WorkOrderTimingBoundsContext workOrderTimingBounds;

    private LocalDate planningStart;

    public MasterPlanSchedule() {
    }

    public MasterPlanSchedule(
            List<TimeSlot> timeSlotRange,
            List<OrderAllocation> orderAllocations,
            LocalDate planningStart,
            MasterPlanSettings planningSettings,
            MaterialFeasibilityContext materialFeasibility,
            MasterPlanObjectiveSettings objectiveSettings,
            List<AdjacentSlotPair> adjacentSlotPairs) {
        this(timeSlotRange, orderAllocations, planningStart, planningSettings, materialFeasibility,
                objectiveSettings, adjacentSlotPairs, MasterPlanCapacityOverlay.empty());
    }

    public MasterPlanSchedule(
            List<TimeSlot> timeSlotRange,
            List<OrderAllocation> orderAllocations,
            LocalDate planningStart,
            MasterPlanSettings planningSettings,
            MaterialFeasibilityContext materialFeasibility,
            MasterPlanObjectiveSettings objectiveSettings,
            List<AdjacentSlotPair> adjacentSlotPairs,
            MasterPlanCapacityOverlay capacityOverlay) {
        this(timeSlotRange, orderAllocations, planningStart, planningSettings, materialFeasibility,
                objectiveSettings, adjacentSlotPairs, capacityOverlay, List.of());
    }

    public MasterPlanSchedule(
            List<TimeSlot> timeSlotRange,
            List<OrderAllocation> orderAllocations,
            LocalDate planningStart,
            MasterPlanSettings planningSettings,
            MaterialFeasibilityContext materialFeasibility,
            MasterPlanObjectiveSettings objectiveSettings,
            List<AdjacentSlotPair> adjacentSlotPairs,
            MasterPlanCapacityOverlay capacityOverlay,
            List<BomDependencyEdge> bomDependencyEdges) {
        this(
                timeSlotRange,
                orderAllocations,
                planningStart,
                planningSettings,
                materialFeasibility,
                objectiveSettings,
                adjacentSlotPairs,
                capacityOverlay,
                bomDependencyEdges,
                WorkOrderTimingBoundsContext.empty());
    }

    public MasterPlanSchedule(
            List<TimeSlot> timeSlotRange,
            List<OrderAllocation> orderAllocations,
            LocalDate planningStart,
            MasterPlanSettings planningSettings,
            MaterialFeasibilityContext materialFeasibility,
            MasterPlanObjectiveSettings objectiveSettings,
            List<AdjacentSlotPair> adjacentSlotPairs,
            MasterPlanCapacityOverlay capacityOverlay,
            List<BomDependencyEdge> bomDependencyEdges,
            WorkOrderTimingBoundsContext workOrderTimingBounds) {
        this(
                timeSlotRange,
                orderAllocations,
                planningStart,
                planningSettings,
                materialFeasibility,
                objectiveSettings,
                adjacentSlotPairs,
                capacityOverlay,
                bomDependencyEdges,
                List.of(),
                workOrderTimingBounds);
    }

    public MasterPlanSchedule(
            List<TimeSlot> timeSlotRange,
            List<OrderAllocation> orderAllocations,
            LocalDate planningStart,
            MasterPlanSettings planningSettings,
            MaterialFeasibilityContext materialFeasibility,
            MasterPlanObjectiveSettings objectiveSettings,
            List<AdjacentSlotPair> adjacentSlotPairs,
            MasterPlanCapacityOverlay capacityOverlay,
            List<BomDependencyEdge> bomDependencyEdges,
            List<OperationPrecedenceEdge> operationPrecedenceEdges,
            WorkOrderTimingBoundsContext workOrderTimingBounds) {
        this.timeSlotRange = timeSlotRange;
        this.orderAllocations = orderAllocations;
        this.planningStart = planningStart;
        this.planningSettings = planningSettings != null ? planningSettings : new MasterPlanSettings();
        this.materialFeasibility = materialFeasibility != null ? materialFeasibility : new MaterialFeasibilityContext(Map.of());
        this.objectiveSettings = objectiveSettings != null ? objectiveSettings : new MasterPlanObjectiveSettings();
        this.adjacentSlotPairs = adjacentSlotPairs != null ? adjacentSlotPairs : List.of();
        this.capacityOverlay = capacityOverlay != null ? capacityOverlay : MasterPlanCapacityOverlay.empty();
        this.bomDependencyEdges = bomDependencyEdges != null ? bomDependencyEdges : List.of();
        this.operationPrecedenceEdges = operationPrecedenceEdges != null ? operationPrecedenceEdges : List.of();
        this.workOrderTimingBounds = workOrderTimingBounds != null
                ? workOrderTimingBounds
                : WorkOrderTimingBoundsContext.empty();
    }

    public List<TimeSlot> getTimeSlotRange() {
        return timeSlotRange;
    }

    public void setTimeSlotRange(List<TimeSlot> timeSlotRange) {
        this.timeSlotRange = timeSlotRange;
    }

    public List<OrderAllocation> getOrderAllocations() {
        return orderAllocations;
    }

    public void setOrderAllocations(List<OrderAllocation> orderAllocations) {
        this.orderAllocations = orderAllocations;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    public LocalDate getPlanningStart() {
        return planningStart;
    }

    public void setPlanningStart(LocalDate planningStart) {
        this.planningStart = planningStart;
    }

    public MasterPlanSettings getPlanningSettings() {
        return planningSettings;
    }

    public void setPlanningSettings(MasterPlanSettings planningSettings) {
        this.planningSettings = planningSettings;
    }

    public MaterialFeasibilityContext getMaterialFeasibility() {
        return materialFeasibility;
    }

    public void setMaterialFeasibility(MaterialFeasibilityContext materialFeasibility) {
        this.materialFeasibility = materialFeasibility;
    }

    public MasterPlanObjectiveSettings getObjectiveSettings() {
        return objectiveSettings;
    }

    public void setObjectiveSettings(MasterPlanObjectiveSettings objectiveSettings) {
        this.objectiveSettings = objectiveSettings;
    }

    public List<AdjacentSlotPair> getAdjacentSlotPairs() {
        return adjacentSlotPairs;
    }

    public void setAdjacentSlotPairs(List<AdjacentSlotPair> adjacentSlotPairs) {
        this.adjacentSlotPairs = adjacentSlotPairs;
    }

    public MasterPlanCapacityOverlay getCapacityOverlay() {
        return capacityOverlay;
    }

    public void setCapacityOverlay(MasterPlanCapacityOverlay capacityOverlay) {
        this.capacityOverlay = capacityOverlay != null ? capacityOverlay : MasterPlanCapacityOverlay.empty();
    }

    public List<BomDependencyEdge> getBomDependencyEdges() {
        return bomDependencyEdges;
    }

    public void setBomDependencyEdges(List<BomDependencyEdge> bomDependencyEdges) {
        this.bomDependencyEdges = bomDependencyEdges != null ? bomDependencyEdges : List.of();
    }

    public List<OperationPrecedenceEdge> getOperationPrecedenceEdges() {
        return operationPrecedenceEdges;
    }

    public void setOperationPrecedenceEdges(List<OperationPrecedenceEdge> operationPrecedenceEdges) {
        this.operationPrecedenceEdges = operationPrecedenceEdges != null ? operationPrecedenceEdges : List.of();
    }

    public WorkOrderTimingBoundsContext getWorkOrderTimingBounds() {
        return workOrderTimingBounds;
    }

    public void setWorkOrderTimingBounds(WorkOrderTimingBoundsContext workOrderTimingBounds) {
        this.workOrderTimingBounds = workOrderTimingBounds != null
                ? workOrderTimingBounds
                : WorkOrderTimingBoundsContext.empty();
    }

    public static MasterPlanSchedule empty() {
        MasterPlanSchedule s = new MasterPlanSchedule();
        s.timeSlotRange = new ArrayList<>();
        s.orderAllocations = new ArrayList<>();
        s.bomDependencyEdges = new ArrayList<>();
        s.operationPrecedenceEdges = new ArrayList<>();
        return s;
    }
}
