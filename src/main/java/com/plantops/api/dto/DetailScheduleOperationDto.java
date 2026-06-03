package com.plantops.api.dto;

public record DetailScheduleOperationDto(
        String operationId,
        String workOrderNo,
        String lineId,
        String resourceId,
        int sequenceIndex,
        Integer startMinute,
        Integer endMinute,
        String productCode,
        boolean pinned,
        String batchNo,
        int operationSeq,
        String operationName,
        Integer changeoverMinutesBefore) {
}
