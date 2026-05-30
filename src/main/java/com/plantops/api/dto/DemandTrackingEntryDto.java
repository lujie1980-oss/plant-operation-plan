package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DemandTrackingEntryDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String customerCode,
        String productCode,
        BigDecimal orderQty,
        LocalDate dueDate,
        LocalDate promiseDate,
        int priority,
        String orderStatus,
        String fulfillmentStatus,
        String kittingStatus,
        int workOrderCount,
        int dispatchedWorkOrderCount,
        int scheduledOperationCount,
        String executionStatus,
        double progressPct,
        List<DemandTrackingFlowStepDto> flowSteps,
        List<DemandTrackingProcessNodeDto> processNodes,
        List<DemandTrackingProcessEdgeDto> processEdges
) {
}
