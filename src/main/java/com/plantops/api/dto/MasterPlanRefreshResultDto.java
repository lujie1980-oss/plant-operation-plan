package com.plantops.api.dto;

import java.time.LocalDate;

public record MasterPlanRefreshResultDto(
        String newMasterPlanVersionId,
        String parentMasterPlanVersionId,
        String detailScheduleVersionId,
        LocalDate feedbackCutoff,
        int frozenAllocationRows,
        int replannedAllocationRows) {
}
