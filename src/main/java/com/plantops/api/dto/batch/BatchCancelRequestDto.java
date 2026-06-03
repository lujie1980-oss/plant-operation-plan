package com.plantops.api.dto.batch;

public record BatchCancelRequestDto(
        String batchNo,
        String workOrderNo,
        boolean cancelAll) {
}
