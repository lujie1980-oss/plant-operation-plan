package com.plantops.api.dto;

import java.math.BigDecimal;

public record WorkOrderKittingLineDto(
        String componentProductCode,
        BigDecimal requiredQty,
        BigDecimal availableQty,
        boolean shortage
) {
}
