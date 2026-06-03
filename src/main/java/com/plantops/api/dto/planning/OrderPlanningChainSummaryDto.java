package com.plantops.api.dto.planning;

import java.time.Instant;
import java.util.Map;

public record OrderPlanningChainSummaryDto(
        String capacityStrategy,
        String inventorySnapshotId,
        int workOrderCount,
        int operationCount,
        Map<String, Integer> issueCountBySeverity,
        Instant computedAt
) {
}
