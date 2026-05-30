package com.plantops.scenario;

/**
 * 计划运行/流水线执行可选步骤。
 */
public record PipelineExecuteOptions(
        boolean includeDetailSchedule,
        boolean refreshMasterPlanAfterSchedule) {

    public static PipelineExecuteOptions masterPlanOnly() {
        return new PipelineExecuteOptions(false, false);
    }

    public static PipelineExecuteOptions fromRequest(Boolean includeDetailSchedule, Boolean refreshAfterSchedule) {
        boolean include = Boolean.TRUE.equals(includeDetailSchedule);
        boolean refresh = Boolean.TRUE.equals(refreshAfterSchedule);
        if (refresh && !include) {
            include = true;
        }
        return new PipelineExecuteOptions(include, refresh);
    }
}
