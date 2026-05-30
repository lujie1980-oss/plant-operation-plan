package com.plantops.api.dto;

import java.math.BigDecimal;

public record BomRequirementDto(
        String componentProductCode,
        BigDecimal requiredQty,
        boolean critical
) {
}
