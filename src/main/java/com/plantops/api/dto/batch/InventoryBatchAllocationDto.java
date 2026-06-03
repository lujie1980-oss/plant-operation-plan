package com.plantops.api.dto.batch;

import java.math.BigDecimal;

/** 某料号被待排批次占用的需求明细。 */
public record InventoryBatchAllocationDto(
        String batchNo,
        String workOrderNo,
        String finishedProductCode,
        BigDecimal batchQuantity,
        BigDecimal workOrderQuantity,
        BigDecimal requiredQty,
        String kittingStatus) {
}
