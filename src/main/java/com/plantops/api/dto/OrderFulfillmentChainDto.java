package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

public record OrderFulfillmentChainDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        LocalDate dueDate,
        LocalDate promiseDate,
        String overallStatus,
        String kittingStatus,
        List<FulfillmentChainNodeDto> nodes,
        List<FulfillmentPegEdgeDto> edges,
        List<UtilizationBucketDto> utilizationBuckets
) {
}
