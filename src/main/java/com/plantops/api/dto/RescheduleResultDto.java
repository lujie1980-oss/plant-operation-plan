package com.plantops.api.dto;

import com.plantops.domain.RescheduleLevel;

import java.util.List;

public record RescheduleResultDto(
        RescheduleLevel level,
        String masterPlanVersionId,
        String detailScheduleVersionId,
        List<String> impactedOrders
) {
}
