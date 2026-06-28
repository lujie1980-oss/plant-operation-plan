package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record MasterRollDto(
        String rollCode,
        BigDecimal widthMm,
        BigDecimal lengthMm,
        BigDecimal thicknessMm,
        String materialCode,
        String productCode,
        String finishedProductCode,
        BigDecimal kerfLongitudinalMm,
        BigDecimal kerfTransverseMm,
        String status) {
}
