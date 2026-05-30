package com.plantops.api.dto;

import java.util.List;

public record WorkOrderGenerationResultDto(
        String salesOrderNo,
        int salesOrderLineNo,
        int workOrdersCreated,
        List<String> workOrderNos
) {
}
