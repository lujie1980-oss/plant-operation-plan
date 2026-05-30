package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 某物料所支撑的上游需求（被谁消耗、何时需要、数量多少） */
public record MaterialDemandUsageDto(
        String demandType,
        String demanderLabel,
        String salesOrderNo,
        int salesOrderLineNo,
        String parentProductCode,
        LocalDate needDate,
        BigDecimal quantity,
        int bomLevel) {
}
