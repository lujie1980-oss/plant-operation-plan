package com.plantops.api.dto;

import java.time.LocalDate;

public record WorkOrderCapacityBucketDto(
        String resourceId,
        LocalDate date,
        String shiftId,
        int demandMinutes,
        int availableMinutes,
        int utilizationPct,
        boolean overloaded
) {
}
