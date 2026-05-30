package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record WorkOrderCapacityGanttDto(
        String workOrderNo,
        String parentWorkOrderNo,
        String workOrderSource,
        String productCode,
        BigDecimal quantity,
        String salesOrderNo,
        int salesOrderLineNo,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs,
        int totalDurationMinutes,
        LocalDateTime horizonStartTs,
        LocalDateTime horizonEndTs,
        WorkOrderTimingWindowDto timingWindow,
        List<WorkOrderCapacityOperationDto> operations,
        List<WorkOrderCapacityBucketDto> resourceBuckets
) {
}
