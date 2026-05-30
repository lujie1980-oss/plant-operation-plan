package com.plantops.api.dto;

public record FulfillmentPegEdgeDto(
        String fromNodeId,
        String toNodeId,
        String pegType,
        String pegLabel
) {
}
