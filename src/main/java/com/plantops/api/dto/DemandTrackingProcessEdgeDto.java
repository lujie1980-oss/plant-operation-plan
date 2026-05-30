package com.plantops.api.dto;

public record DemandTrackingProcessEdgeDto(
        String fromNodeId,
        String toNodeId
) {
}
