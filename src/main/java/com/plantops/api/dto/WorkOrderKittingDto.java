package com.plantops.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record WorkOrderKittingDto(
        String workOrderNo,
        String productCode,
        BigDecimal quantity,
        String dispatchStatus,
        String kittingStatus,
        String shortageReason,
        List<WorkOrderKittingLineDto> lines
) {
}
