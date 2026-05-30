package com.plantops.api.dto.planning;

import java.util.List;

public record PlanningConstraintMatchTotalDto(
        String constraintId,
        String constraintPackage,
        String constraintName,
        int hardScore,
        int softScore,
        int matchCount,
        List<PlanningConstraintMatchDto> sampleMatches,
        boolean sampleTruncated
) {
}
