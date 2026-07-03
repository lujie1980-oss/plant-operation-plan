package com.plantops.api.dto.slitting;

import java.math.BigDecimal;
import java.util.List;

public record SlittingSessionDto(
        String sessionId,
        String planVersionId,
        String activeParentNodeId,
        String score,
        Long lastOptimizeMs,
        BigDecimal utilizationPct,
        List<SlittingAssignmentDto> assignments) {
}
