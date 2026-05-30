package com.plantops.api.dto.planning;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Timefold 选优层得分分解：由 {@code SolutionManager.explain()} 投影，用于解释已持久化计划版本的约束匹配。
 */
public record PlanningScoreExplanationDto(
        LocalDateTime computedAt,
        String planVersionId,
        String planType,
        String masterPlanVersionId,
        String score,
        int hardScore,
        int softScore,
        String summary,
        List<PlanningConstraintMatchTotalDto> constraintTotals,
        boolean matchesTruncated
) {
}
