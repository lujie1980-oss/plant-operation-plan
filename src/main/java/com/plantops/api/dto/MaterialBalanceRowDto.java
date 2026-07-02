package com.plantops.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record MaterialBalanceRowDto(
        String productCode,
        String pispId,
        boolean critical,
        BigDecimal totalShortageQty,
        List<MaterialBalanceDayDto> days,
        List<MaterialBalancePeriodDto> periods) {
}
