package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MaterialBalanceDayDto(
        LocalDate date,
        BigDecimal openingQty,
        BigDecimal demandQty,
        BigDecimal supplyQty,
        BigDecimal closingQty,
        BigDecimal shortageQty) {
}
