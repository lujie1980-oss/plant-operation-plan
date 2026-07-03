package com.plantops.scenario;

/**
 * 需求满足页订单行右键动作。
 */
public enum OrderDemandAction {
    /** 无限能力 JIT：按 Demand 交期倒排并创建上游 SupplyOrder。 */
    INFINITE_PLAN_JIT,
    /** 有限能力：单交付 Timefold 优化，不改动其他订单已排结果。 */
    FINITE_PLAN,
    CONFIRM_PROMISE_DATE,
    CANCEL_PLAN,
    /** 取消承诺交期（SCN-01f）；不删除计划工单或 pegging。 */
    CANCEL_PROMISE,
    /** @deprecated 使用 {@link #INFINITE_PLAN_JIT} */
    BUILD_UPSTREAM_CHAIN,
    /** @deprecated 全场景无限能力预览 */
    PLAN_UNCONSTRAINED,
    /** @deprecated 使用 {@link #FINITE_PLAN} */
    PLAN_FINITE;

    public static OrderDemandAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("action 不能为空");
        }
        String key = raw.trim().toUpperCase();
        return switch (key) {
            case "BUILD_UPSTREAM_CHAIN" -> INFINITE_PLAN_JIT;
            case "PLAN_UNCONSTRAINED" -> PLAN_UNCONSTRAINED;
            case "PLAN_FINITE" -> FINITE_PLAN;
            default -> valueOf(key);
        };
    }
}
