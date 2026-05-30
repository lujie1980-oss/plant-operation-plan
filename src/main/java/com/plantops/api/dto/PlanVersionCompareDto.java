package com.plantops.api.dto;

import java.util.List;

public record PlanVersionCompareDto(
        String fromVersionId,
        String toVersionId,
        String fromScore,
        String toScore,
        List<String> impactSummary
) {
}
