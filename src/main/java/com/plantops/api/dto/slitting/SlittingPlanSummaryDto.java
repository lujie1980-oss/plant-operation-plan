package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record SlittingPlanSummaryDto(
        String planVersionId,
        String name,
        String status,
        String score,
        BigDecimal utilizationPct,
        Long solveDurationMs,
        String solverPhase) {
}
