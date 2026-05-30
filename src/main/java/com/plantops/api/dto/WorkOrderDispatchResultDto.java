package com.plantops.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderDispatchResultDto(
        int dispatchedCount,
        LocalDateTime dispatchedTs,
        List<String> workOrderNos
) {
}
