package com.plantops.api.dto.demand;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;

import java.time.LocalDate;

public record OrderDemandActionResult(
        String action,
        String message,
        OrderFulfillmentChainDto fulfillmentChain,
        LocalDate confirmedPromiseDate,
        WorkOrderGenerationResultDto workOrderGeneration
) {
}
