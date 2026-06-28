package com.plantops.api.dto.slitting;

public record SlittingBomScopeDto(
        String scopeId,
        String scopeType,
        String label,
        String finishedProductCode,
        String productCode) {
}
