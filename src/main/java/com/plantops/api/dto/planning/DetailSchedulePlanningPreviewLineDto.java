package com.plantops.api.dto.planning;

/** 推演层产线域（P1）快照。 */
public record DetailSchedulePlanningPreviewLineDto(
        String lineId,
        String resourceId,
        String areaId,
        boolean opened,
        int capacityMinutes,
        int queuedOperationCount) {
}
