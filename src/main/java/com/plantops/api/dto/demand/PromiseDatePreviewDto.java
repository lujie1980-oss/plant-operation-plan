package com.plantops.api.dto.demand;

import com.plantops.api.dto.OrderFulfillmentChainDto;

import java.time.LocalDate;

public record PromiseDatePreviewDto(
        OrderFulfillmentChainDto fulfillmentChain,
        LocalDate suggestedPromiseDate,
        String overallStatus) {
}
