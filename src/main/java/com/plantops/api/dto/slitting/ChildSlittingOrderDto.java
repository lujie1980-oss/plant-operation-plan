package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record ChildSlittingOrderDto(
        String orderCode,
        BigDecimal widthMm,
        BigDecimal lengthMm,
        BigDecimal thicknessMm,
        int quantity,
        int priority,
        String salesOrderNo,
        Integer salesOrderLineNo,
        String workOrderNo,
        String productCode,
        String finishedProductCode,
        String status) {
}
