package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

public record LoadBucketDto(
        String bucketId,
        String resourceId,
        String resourceLabel,
        LocalDate date,
        String shiftId,
        int demandMinutes,
        /** 排程反馈已冻结占用（分钟） */
        int feedbackLockedMinutes,
        int availableMinutes,
        int utilizationPct,
        boolean overloaded,
        List<CapacityBucketWorkOrderDto> workOrders
) {

    public int replannableDemandMinutes() {
        return Math.max(0, demandMinutes - feedbackLockedMinutes);
    }
}
