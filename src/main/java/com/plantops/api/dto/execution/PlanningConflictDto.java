package com.plantops.api.dto.execution;

import java.time.LocalDateTime;

public record PlanningConflictDto(
        String conflictId,
        String stepId,
        String planVersionId,
        String reasonCode,
        String message,
        LocalDateTime detectedTs,
        boolean resolved) {
}
