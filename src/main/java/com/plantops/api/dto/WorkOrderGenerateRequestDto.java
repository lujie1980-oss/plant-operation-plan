package com.plantops.api.dto;

public record WorkOrderGenerateRequestDto(
        String salesOrderNo,
        Integer salesOrderLineNo,
        Boolean replaceExisting,
        Boolean allOpenOrders
) {
}
