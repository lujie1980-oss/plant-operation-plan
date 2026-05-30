package com.plantops.api.dto;

import java.time.LocalDate;

public record ScheduleFeedbackApplyResultDto(
        String feedbackBatchId,
        String detailScheduleVersionId,
        String masterPlanVersionId,
        LocalDate cutoffDate,
        int operationCount,
        int frozenCount,
        int suggestionCount) {
}
