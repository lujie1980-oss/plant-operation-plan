package com.plantops.scenario;

/**
 * 需求满足页订单行右键动作。
 */
public enum OrderDemandAction {
    BUILD_UPSTREAM_CHAIN,
    PLAN_UNCONSTRAINED,
    PLAN_FINITE,
    CONFIRM_PROMISE_DATE,
    CANCEL_PLAN;

    public static OrderDemandAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("action 不能为空");
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
