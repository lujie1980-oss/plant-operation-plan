package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record SlittingMaterialDemandDto(
        String demandType,
        String demandId,
        String label,
        String productCode,
        String finishedProductCode,
        BigDecimal quantity,
        String salesOrderNo,
        Integer salesOrderLineNo,
        String relation) {
}
