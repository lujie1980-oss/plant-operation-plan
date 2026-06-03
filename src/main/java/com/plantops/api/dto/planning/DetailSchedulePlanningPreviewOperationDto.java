package com.plantops.api.dto.planning;

import java.time.LocalDate;

/** 推演层工序候选 + 可选求解后的产线/时间（与持久化 {@link com.plantops.api.dto.DetailScheduleOperationDto} 对齐字段）。 */
public record DetailSchedulePlanningPreviewOperationDto(
        String operationId,
        String workOrderNo,
        String batchNo,
        String productCode,
        String operationName,
        int operationSeq,
        String resourceId,
        String lineId,
        Integer sequenceOnLine,
        Integer startMinute,
        Integer endMinute,
        boolean scheduled,
        boolean kittingEligible,
        int earliestStartMinute,
        boolean pinned,
        LocalDate mpContractStartDate,
        LocalDate mpContractEndDate,
        LocalDate mpTargetEndDate,
        Integer changeoverMinutesBefore) {
}
