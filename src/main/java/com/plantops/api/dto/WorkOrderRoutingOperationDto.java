package com.plantops.api.dto;

import java.util.List;

/** 工单工艺路径中的一道工序。 */
public record WorkOrderRoutingOperationDto(
        int sequenceNo,
        String operationName,
        List<WorkOrderRoutingResourceOptionDto> resourceOptions) {
}
