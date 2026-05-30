package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record FulfillmentChainNodeDto(
        String nodeId,
        String nodeType,
        String laneId,
        String label,
        String status,
        int depth,
        String productCode,
        BigDecimal quantity,
        LocalDateTime startTs,
        LocalDateTime endTs,
        Map<String, Object> attributes,
        List<FulfillmentOperationDto> operations
) {
}
