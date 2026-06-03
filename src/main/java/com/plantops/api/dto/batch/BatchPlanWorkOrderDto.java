package com.plantops.api.dto.batch;

import java.math.BigDecimal;

public record BatchPlanWorkOrderDto(
        String workOrderNo,
        String productCode,
        BigDecimal quantity,
        BigDecimal batchedQuantity,
        BigDecimal remainingQuantity,
        String batchSplitStatus,
        boolean pendingScheduleEligible,
        String dispatchStatus) {
}
