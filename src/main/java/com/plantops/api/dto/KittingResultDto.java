package com.plantops.api.dto;

public record KittingResultDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String kittingStatus,
        String shortageReason
) {
}
