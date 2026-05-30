package com.plantops.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MaterialBalanceRowDto(
        String productCode,
        boolean critical,
        BigDecimal totalShortageQty,
        List<MaterialBalanceDayDto> days) {
}
