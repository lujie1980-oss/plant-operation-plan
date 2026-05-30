package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MasterPlanAllocationDto(
        String allocationId,
        int segmentIndex,
        String workOrderNo,
        String parentWorkOrderNo,
        String workOrderSource,
        String productCode,
        BigDecimal quantity,
        String salesOrderNo,
        int salesOrderLineNo,
        String resourceId,
        int slotIndex,
        LocalDate slotDate,
        String shiftId,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs,
        int durationMinutes
) {
}
