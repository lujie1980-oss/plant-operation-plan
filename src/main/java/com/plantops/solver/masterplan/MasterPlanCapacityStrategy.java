package com.plantops.solver.masterplan;

/**
 * 主计划产能策略：
 * <ul>
 *   <li>{@link #UNCONSTRAINED} — 不限制槽位总负荷，允许单日超负荷（与历史行为一致）</li>
 *   <li>{@link #FINITE_CAPACITY} — 槽位总负荷不得超过日历产能；超长工单拆成多段跨天分配</li>
 * </ul>
 */
public enum MasterPlanCapacityStrategy {

    UNCONSTRAINED,
    FINITE_CAPACITY;

    public static MasterPlanCapacityStrategy fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNCONSTRAINED;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "FINITE_CAPACITY", "CAPACITY", "CONSTRAINED", "FINITE" -> FINITE_CAPACITY;
            case "UNCONSTRAINED", "NONE", "NO_CAPACITY", "ALLOW_OVERLOAD" -> UNCONSTRAINED;
            default -> throw new IllegalArgumentException("Unknown master plan capacity strategy: " + raw);
        };
    }

    public boolean isCapacityConstrained() {
        return this == FINITE_CAPACITY;
    }
}
