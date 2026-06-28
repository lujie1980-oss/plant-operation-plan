package com.plantops.api.dto;

import java.time.LocalDate;

public record CustomerOrderLineDeliveryListItemDto(
        String deliveryId,
        String customerOrderLineId,
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        double deliveryQty,
        LocalDate requestedDate,
        LocalDate latestDesiredDate,
        LocalDate promiseDate,
        int priority,
        String status,
        String kittingStatus,
        String fulfillmentStatus
) {
}
