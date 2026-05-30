package com.plantops.api.dto;

/**
 * @param strategyId 主计划策略 ID（优先）
 * @param capacityStrategy 兼容旧客户端：{@code UNCONSTRAINED} 或 {@code FINITE_CAPACITY}
 * @param includeDetailSchedule 主计划完成后是否继续求解详细排程
 * @param refreshMasterPlanAfterSchedule 排程完成后是否按反馈滚动刷新主计划（需 {@code includeDetailSchedule=true}）
 */
public record PipelineRunRequest(
        String strategyId,
        String capacityStrategy,
        String scenarioId,
        String ruleSetVersionId,
        Boolean includeDetailSchedule,
        Boolean refreshMasterPlanAfterSchedule) {

    public PipelineRunRequest(String strategyId, String capacityStrategy) {
        this(strategyId, capacityStrategy, null, null, false, false);
    }
}
