package com.plantops.api.dto.planning;

/**
 * 主计划推演层预览：默认仅 P0–P4 诊断 + 分配候选；可选内存求解或持久化求解。
 */
public record MasterPlanPlanningPreviewRequest(
        String strategyId,
        /** 为 true 时调用 Timefold（默认 false） */
        Boolean solve,
        /** 仅当 solve=true：是否落库为正式主计划版本（默认 false） */
        Boolean persist,
        /** 非空时构建反馈产能 overlay（与滚动刷新主计划一致） */
        String feedbackCutoff) {

    public boolean resolveSolve() {
        return Boolean.TRUE.equals(solve);
    }

    public boolean resolvePersist() {
        return Boolean.TRUE.equals(persist);
    }
}
