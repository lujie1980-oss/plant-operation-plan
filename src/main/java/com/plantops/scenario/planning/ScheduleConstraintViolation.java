package com.plantops.scenario.planning;

/**
 * 内存推演/校验发现的约束违背（不依赖 Timefold score）。
 */
public record ScheduleConstraintViolation(
        ViolationLevel level,
        String ruleCode,
        String operationId,
        String lineId,
        String message) {

    public enum ViolationLevel {
        HARD,
        MEDIUM,
        SOFT,
        INFO
    }
}
