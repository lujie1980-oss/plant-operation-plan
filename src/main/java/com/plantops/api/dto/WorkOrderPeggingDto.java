package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkOrderPeggingDto(
        Long id,
        String workOrderNo,
        String salesOrderNo,
        int salesOrderLineNo,
        String finishedProductCode,
        BigDecimal peggedQty,
        LocalDate needDate) {
}
