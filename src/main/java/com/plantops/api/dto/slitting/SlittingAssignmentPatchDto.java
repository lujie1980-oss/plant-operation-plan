package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record SlittingAssignmentPatchDto(
        String assignmentId,
        BigDecimal posXMm,
        BigDecimal posYMm,
        Boolean rotated,
        Boolean pinned) {
}
