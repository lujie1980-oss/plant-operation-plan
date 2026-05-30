package com.plantops.api.dto;

import java.util.List;
import java.util.Map;

public record ShortageRecommendationDto(
        String shortageId,
        String shortageType,
        String severity,
        String areaId,
        String shiftId,
        String lineId,
        Map<String, Object> evidence,
        String recommendedAction,
        List<String> impactOrders
) {
}
