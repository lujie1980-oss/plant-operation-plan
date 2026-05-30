package com.plantops.api.dto;

import java.time.LocalDateTime;

/** 工单工序排程结果（来自排程反馈或详细排程）。 */
public record WorkOrderScheduleOperationDto(
        String operationId,
        int operationSeq,
        String operationName,
        String resourceId,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        int durationMinutes,
        String scope) {
}
