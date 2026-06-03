package com.plantops.api.dto.batch;

import java.math.BigDecimal;

public record ManualBatchCreateRequestDto(
        String workOrderNo,
        BigDecimal quantity) {
}
