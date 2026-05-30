package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleContractSettings;
import com.plantops.solver.detailschedule.ScheduleLine;

import java.time.LocalDate;
import java.util.List;

/**
 * 详细排程单次求解的推演快照（P0–P4）：产线、工序候选、契约与齐套标记，不含 Timefold 得分。
 * Timefold 输入由 {@link DetailScheduleProblemMapper} 投影。
 */
public final class DetailSchedulePlanningContext {

    private final LocalDate planningAnchor;
    private final int shiftCapacityMinutes;
    private final ScheduleContractSettings contractSettings;
    private final List<ScheduleLine> lines;
    private final List<OperationAssignment> operations;
    private final DetailSchedulePlanningDiagnosticsDto diagnostics;
    private final MaterialPlanningContext materialPlanning;

    public DetailSchedulePlanningContext(
            LocalDate planningAnchor,
            int shiftCapacityMinutes,
            ScheduleContractSettings contractSettings,
            List<ScheduleLine> lines,
            List<OperationAssignment> operations,
            DetailSchedulePlanningDiagnosticsDto diagnostics,
            MaterialPlanningContext materialPlanning) {
        this.planningAnchor = planningAnchor != null ? planningAnchor : LocalDate.now();
        this.shiftCapacityMinutes = shiftCapacityMinutes;
        this.contractSettings = contractSettings;
        this.lines = lines != null ? List.copyOf(lines) : List.of();
        this.operations = operations != null ? List.copyOf(operations) : List.of();
        this.diagnostics = diagnostics;
        this.materialPlanning = materialPlanning;
    }

    public DetailSchedulePlanningContext(
            LocalDate planningAnchor,
            int shiftCapacityMinutes,
            ScheduleContractSettings contractSettings,
            List<ScheduleLine> lines,
            List<OperationAssignment> operations,
            DetailSchedulePlanningDiagnosticsDto diagnostics) {
        this(planningAnchor, shiftCapacityMinutes, contractSettings, lines, operations, diagnostics, null);
    }

    public LocalDate planningAnchor() {
        return planningAnchor;
    }

    public int shiftCapacityMinutes() {
        return shiftCapacityMinutes;
    }

    public ScheduleContractSettings contractSettings() {
        return contractSettings;
    }

    public List<ScheduleLine> lines() {
        return lines;
    }

    public List<OperationAssignment> operations() {
        return operations;
    }

    public DetailSchedulePlanningDiagnosticsDto diagnostics() {
        return diagnostics;
    }

    public MaterialPlanningContext materialPlanning() {
        return materialPlanning;
    }
}
