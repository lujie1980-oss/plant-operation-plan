package com.plantops.api.dto;

import java.time.LocalDateTime;

/** 工单时间窗口：最晚要求 / 最早可行 / 无上游限制的最早可行。 */
public record WorkOrderTimingWindowDto(
        LocalDateTime latestDesiredStart,
        LocalDateTime latestDesiredEnd,
        LocalDateTime latestDesiredDelivery,
        LocalDateTime earliestPossibleStart,
        LocalDateTime earliestPossibleEnd,
        LocalDateTime earliestPossibleDelivery,
        LocalDateTime earliestPossibleStartOwn,
        LocalDateTime earliestPossibleEndOwn,
        LocalDateTime earliestPossibleDeliveryOwn,
        int productionDurationMinutes,
        int postProcessingMinutes) {
}
