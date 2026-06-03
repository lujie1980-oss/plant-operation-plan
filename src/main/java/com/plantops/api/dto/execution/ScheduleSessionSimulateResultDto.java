package com.plantops.api.dto.execution;

import com.plantops.api.dto.planning.ScheduleConstraintViolationDto;

import java.util.List;

public record ScheduleSessionSimulateResultDto(
        ScheduleSessionDto session,
        String simulationMode,
        long simulationDurationMs,
        List<String> recalculatedOperationIds,
        List<ScheduleConstraintViolationDto> violations,
        int hardViolationCount,
        int mediumViolationCount) {
}
