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
        List<UtilizationBucketDto> utilizationBuckets,
        String deliveryId
) {
    public OrderFulfillmentChainDto(
            String salesOrderNo,
            int salesOrderLineNo,
            String productCode,
            LocalDate dueDate,
            LocalDate promiseDate,
            String overallStatus,
            String kittingStatus,
            List<FulfillmentChainNodeDto> nodes,
            List<FulfillmentPegEdgeDto> edges,
            List<UtilizationBucketDto> utilizationBuckets) {
        this(
                salesOrderNo,
                salesOrderLineNo,
                productCode,
                dueDate,
                promiseDate,
                overallStatus,
                kittingStatus,
                nodes,
                edges,
                utilizationBuckets,
                null);
    }
}
