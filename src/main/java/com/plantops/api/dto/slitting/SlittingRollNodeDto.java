package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record SlittingRollNodeDto(
        String nodeId,
        String nodeType,
        String parentNodeId,
        BigDecimal widthMm,
        BigDecimal lengthMm,
        BigDecimal thicknessMm,
        String cuttingMethod,
        String sourceSpecCode) {
}
