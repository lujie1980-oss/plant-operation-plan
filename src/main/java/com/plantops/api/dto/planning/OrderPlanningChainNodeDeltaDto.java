package com.plantops.api.dto.planning;

import java.time.LocalDate;

public record OrderPlanningChainNodeDeltaDto(
        String nodeId,
        LocalDate baselineStart,
        LocalDate baselineEnd,
        LocalDate trialStart,
        LocalDate trialEnd,
        boolean statusChanged
) {
}
