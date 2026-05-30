package com.plantops.api.dto;

import java.time.LocalDateTime;

public record UtilizationBucketDto(
        String resourceId,
        LocalDateTime bucketStart,
        LocalDateTime bucketEnd,
        int demandMinutes,
        int availableMinutes,
        int utilizationPct
) {
}
