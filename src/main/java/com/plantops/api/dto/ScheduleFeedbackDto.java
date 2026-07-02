package com.plantops.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ScheduleFeedbackDto(
        String feedbackId,
        String masterPlanVersionId,
        String detailScheduleVersionId,
        String workOrderNo,
        int operationSeq,
        String operationId,
        String resourceId,
        String physicalResourceId,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDate slotDate,
        int durationMinutes,
        String scope,
        LocalDate planningAnchorDate,
        LocalDateTime feedbackTs) {
}
