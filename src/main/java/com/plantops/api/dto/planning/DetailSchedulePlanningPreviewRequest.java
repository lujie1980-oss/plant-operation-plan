package com.plantops.api.dto.planning;

/**
 * 细排程推演层预览：默认仅 P0–P4 诊断 + 工序候选；可选内存求解或持久化求解。
 */
public record DetailSchedulePlanningPreviewRequest(
        String masterPlanVersionId,
        /** 为 true 时调用 Timefold（默认 false） */
        Boolean solve,
        /** 仅当 solve=true：是否落库为正式排程版本（默认 false） */
        Boolean persist,
        /** 仅当 solve=true 且 persist=true：排程后滚动刷新主计划 */
        Boolean refreshMasterPlanAfter,
        /** 仅当 refreshMasterPlanAfter 时生效，ISO 日期 */
        String feedbackCutoff,
        /**
         * 为 true 且 solve=false：仅做初始队列种子 + 链式赋时（不选优），用于推演态甘特试看。
         */
        Boolean seedInitialQueues) {

    public boolean resolveSolve() {
        return Boolean.TRUE.equals(solve);
    }

    public boolean resolvePersist() {
        return Boolean.TRUE.equals(persist);
    }

    public boolean resolveRefreshMasterPlanAfter() {
        return Boolean.TRUE.equals(refreshMasterPlanAfter);
    }

    public boolean resolveSeedInitialQueues() {
        return Boolean.TRUE.equals(seedInitialQueues);
    }
}
