package com.plantops.api.dto;

import java.math.BigDecimal;

/** 某料号被已下发工单占用的需求明细。 */
public record InventoryWorkOrderAllocationDto(
        String workOrderNo,
        String finishedProductCode,
        BigDecimal workOrderQuantity,
        BigDecimal requiredQty,
        String kittingStatus) {
}
