package com.plantops.scenario;

/** 主计划并行工序绑定结果。 */
public record MasterPlanParallelBindingResult(
        int pairedGroups,
        int orphans,
        int slotIntersectionsApplied,
        int slotIntersectionFallbacks) {
}
