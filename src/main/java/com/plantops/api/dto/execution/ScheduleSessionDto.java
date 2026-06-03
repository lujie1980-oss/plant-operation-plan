package com.plantops.api.dto.execution;

import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewDto;

import java.time.LocalDateTime;

public record ScheduleSessionDto(
        String sessionId,
        String masterPlanVersionId,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        DetailSchedulePlanningPreviewDto preview) {
}
