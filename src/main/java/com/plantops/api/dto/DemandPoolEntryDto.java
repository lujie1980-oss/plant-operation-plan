package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DemandPoolEntryDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        BigDecimal orderQty,
        LocalDate dueDate,
        LocalDate promiseDate,
        int priority,
        int expediteLevel,
        String status,
        boolean scheduleLockFlag,
        String kittingStatus,
        String fulfillmentStatus,
        List<BomRequirementDto> bomRequirements
) {
}
