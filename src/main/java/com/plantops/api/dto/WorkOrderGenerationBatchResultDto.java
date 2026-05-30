package com.plantops.api.dto;

import java.util.List;

public record WorkOrderGenerationBatchResultDto(
        int orderLinesProcessed,
        int workOrdersCreated,
        List<WorkOrderGenerationResultDto> details
) {
}
