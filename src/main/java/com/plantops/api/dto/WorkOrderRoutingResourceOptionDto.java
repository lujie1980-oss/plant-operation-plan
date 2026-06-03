package com.plantops.api.dto;

import java.util.List;

/** 工序可选资源及对应可用产线。 */
public record WorkOrderRoutingResourceOptionDto(
        String resourceId,
        int resourcePriority,
        int durationMinutes,
        List<String> allowedLineIds) {
}
