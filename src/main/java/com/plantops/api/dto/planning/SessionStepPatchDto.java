package com.plantops.api.dto.planning;

/**
 * Session 内单步手动调整：改产线、队列顺序（1-based）、锁定。
 */
public record SessionStepPatchDto(
        String stepId,
        String lineId,
        Integer sequenceOnLine,
        Boolean pinned) {
}
