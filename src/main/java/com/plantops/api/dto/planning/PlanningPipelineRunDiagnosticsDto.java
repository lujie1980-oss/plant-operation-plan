package com.plantops.api.dto.planning;

/**
 * 流水线运行结束时持久化的 S04/S05 推演诊断快照（JSON 存于 planning_pipeline_run.diagnostics_json）。
 */
public record PlanningPipelineRunDiagnosticsDto(
        MasterPlanPlanningDiagnosticsDto masterPlan,
        DetailSchedulePlanningDiagnosticsDto detailSchedule
) {
}
