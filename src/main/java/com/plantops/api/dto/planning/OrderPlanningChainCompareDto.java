package com.plantops.api.dto.planning;

import java.util.List;

public record OrderPlanningChainCompareDto(
        String baselineVersionId,
        List<OrderPlanningChainNodeDeltaDto> nodeDeltas
) {
}
