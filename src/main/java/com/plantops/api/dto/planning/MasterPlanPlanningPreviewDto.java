package com.plantops.api.dto.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主计划推演层统一响应：诊断 + 分配快照；{@code solve=true} 时含选优结果（反写到同一批 {@code OrderAllocation}）。
 */
public record MasterPlanPlanningPreviewDto(
        LocalDateTime computedAt,
        LocalDate planningStart,
        String strategyId,
        String strategyName,
        String capacityStrategy,
        boolean overlayActive,
        boolean solved,
        boolean persisted,
        String planVersionId,
        String score,
        Long solveDurationMs,
        MasterPlanPlanningDiagnosticsDto diagnostics,
        List<MasterPlanPlanningPreviewAllocationDto> allocations,
        int allocationCount,
        int scheduledAllocationCount) {
}
