package com.plantops.api.dto.batch;

import java.math.BigDecimal;
import java.util.List;

public record BatchSplitResultDto(
        String workOrderNo,
        String batchSplitStatus,
        BigDecimal remainingQuantity,
        List<ProductionBatchDto> batches) {
}
