package com.plantops.scenario.execution;

/**
 * 批次工序（Batch Process Step）车间执行态。
 */
public enum StepExecutionState {
    UNPLANNED,
    RELEASED,
    RUNNING,
    COMPLETED,
    ARCHIVED;

    public static StepExecutionState parse(String value) {
        if (value == null || value.isBlank()) {
            return UNPLANNED;
        }
        return StepExecutionState.valueOf(value.trim().toUpperCase());
    }
}
