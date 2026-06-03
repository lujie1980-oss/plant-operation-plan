package com.plantops.api.dto.demand;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;

import java.time.LocalDate;

public record OrderDemandActionRequest(
        String masterPlanVersionId,
        LocalDate promiseDateOverride,
        Boolean useFeedbackOverlay,
        LocalDate feedbackCutoff
) {
}
