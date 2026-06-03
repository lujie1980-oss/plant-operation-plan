package com.plantops.api.dto.execution;

import java.time.LocalDateTime;
import java.util.List;

public record ConfirmScheduleSessionResultDto(
        String planVersionId,
        int releasedCount,
        List<PlanningConflictDto> conflicts) {
}
