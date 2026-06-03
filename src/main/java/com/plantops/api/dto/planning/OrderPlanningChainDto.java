package com.plantops.api.dto.planning;

import com.plantops.api.dto.FulfillmentPegEdgeDto;

import java.time.LocalDate;
import java.util.List;

public record OrderPlanningChainDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        LocalDate dueDate,
        LocalDate promiseDate,
        String overallStatus,
        String kittingStatus,
        OrderPlanningChainSummaryDto summary,
        List<OrderPlanningChainNodeDto> nodes,
        List<FulfillmentPegEdgeDto> edges,
        OrderPlanningChainCompareDto compare
) {
}
