package com.plantops.api.dto;

public record DemandTrackingFlowStepDto(
        String stepId,
        String label,
        String status,
        String detail
) {
}
