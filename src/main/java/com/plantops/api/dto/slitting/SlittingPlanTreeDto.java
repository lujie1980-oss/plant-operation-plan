package com.plantops.api.dto.slitting;

import java.math.BigDecimal;
import java.util.List;

public record SlittingPlanTreeDto(
        String planVersionId,
        List<SlittingRollNodeDto> nodes,
        List<SlittingAssignmentDto> assignments,
        BigDecimal utilizationPct) {
}
