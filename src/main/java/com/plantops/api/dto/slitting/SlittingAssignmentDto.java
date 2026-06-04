package com.plantops.api.dto.slitting;

import java.math.BigDecimal;

public record SlittingAssignmentDto(
        String assignmentId,
        String childNodeId,
        String parentNodeId,
        BigDecimal posXMm,
        BigDecimal posYMm,
        boolean rotated,
        Integer sequence) {
}
