package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record IntermediateRollCatalogDto(
        String specCode,
        BigDecimal widthMm,
        BigDecimal lengthMm,
        String cuttingMethod,
        BigDecimal kerfMm,
        boolean active) {
}
