package com.plantops.api.dto.batch;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductionBatchDto(
        Long id,
        String batchNo,
        String workOrderNo,
        int batchSeq,
        BigDecimal quantity,
        String kittingStatus,
        String splitMethod,
        String status,
        boolean pendingScheduleEligible,
        LocalDateTime createdTs) {
}
