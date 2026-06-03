package com.plantops.api.dto.planning;

public record ScheduleConstraintViolationDto(
        String level,
        String ruleCode,
        String operationId,
        String lineId,
        String message) {
}
