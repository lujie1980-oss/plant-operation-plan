package com.plantops.api.dto.planning;

import com.plantops.api.dto.FulfillmentOperationDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OrderPlanningChainNodeDto(
        String nodeId,
        String nodeType,
        String laneId,
        String label,
        String status,
        int depth,
        String productCode,
        BigDecimal quantity,
        LocalDate windowStart,
        LocalDate windowEnd,
        String planningLayer,
        List<PlanningSignalDto> planningSignals,
        Map<String, Object> attributes,
        List<FulfillmentOperationDto> operations
) {
}
