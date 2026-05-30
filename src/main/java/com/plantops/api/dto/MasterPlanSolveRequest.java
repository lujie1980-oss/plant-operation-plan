package com.plantops.api.dto;

/**
 * @param strategyId 主计划策略 ID（优先）
 * @param capacityStrategy 兼容旧客户端
 */
public record MasterPlanSolveRequest(String strategyId, String capacityStrategy) {
}
