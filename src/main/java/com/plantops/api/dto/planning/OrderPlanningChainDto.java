package com.plantops.api.dto.planning;

import com.plantops.api.dto.FulfillmentPegEdgeDto;

import java.time.LocalDate;
import java.util.List;

/**
 * @deprecated M5 Phase 2 — 使用 {@link com.plantops.api.dto.OrderFulfillmentChainDto} 与
 * {@link com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxService}。
 */
@Deprecated(since = "1.0", forRemoval = true)
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
