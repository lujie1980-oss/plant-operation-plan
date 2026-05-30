package com.plantops.api.dto.planning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 详细排程推演层（S05）诊断快照。 */
public record DetailSchedulePlanningDiagnosticsDto(
        LocalDateTime computedAt,
        String masterPlanVersionId,
        String inventorySnapshotId,
        Map<String, Integer> counters,
        List<PlanningDiagnosticIssue> issues,
        boolean issuesTruncated
) {
}
