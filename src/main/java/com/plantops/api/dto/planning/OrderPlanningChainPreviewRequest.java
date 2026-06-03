package com.plantops.api.dto.planning;

import java.time.LocalDate;

public record OrderPlanningChainPreviewRequest(
        String salesOrderNo,
        int salesOrderLineNo,
        String masterPlanStrategyId,
        Boolean useFeedbackOverlay,
        LocalDate feedbackCutoff,
        String detailScheduleMasterPlanVersionId,
        String baselineMasterPlanVersionId
) {
}
