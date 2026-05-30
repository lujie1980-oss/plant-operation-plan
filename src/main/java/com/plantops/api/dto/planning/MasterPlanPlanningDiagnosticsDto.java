package com.plantops.api.dto.planning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 主计划推演层（S04）诊断快照：在 Timefold 求解前采集，用于解释「未生成分配 / 无 eligible 槽位」等问题。
 */
public record MasterPlanPlanningDiagnosticsDto(
        LocalDateTime computedAt,
        String capacityStrategy,
        boolean overlayActive,
        String inventorySnapshotId,
        Map<String, Integer> counters,
        List<PlanningDiagnosticIssue> issues,
        boolean issuesTruncated
) {
}
