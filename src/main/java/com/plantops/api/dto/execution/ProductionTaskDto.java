package com.plantops.api.dto.execution;

import java.time.LocalDateTime;

public record ProductionTaskDto(
        String stepId,
        String batchNo,
        String workOrderNo,
        int operationSeq,
        String operationName,
        String productCode,
        String lineId,
        String resourceId,
        java.math.BigDecimal quantity,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs,
        String planVersionId,
        String executionState,
        LocalDateTime releasedTs,
        LocalDateTime actualStartTs,
        LocalDateTime actualEndTs,
        LocalDateTime updatedTs) {
}
