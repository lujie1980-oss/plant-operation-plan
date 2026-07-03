package com.plantops.api.dto;

import java.math.BigDecimal;

public record MaterialBalancePeriodDto(
        String periodId,
        BigDecimal openingQty,
        BigDecimal demandQty,
        BigDecimal supplyQty,
        BigDecimal closingQty,
        BigDecimal shortageQty) {
}
