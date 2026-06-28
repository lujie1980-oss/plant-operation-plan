package com.plantops.scenario.planning.optimizer;

import java.time.LocalDateTime;

/** 求解器无关的工序/工单时间分配结果。 */
public record PlanningAssignment(
        String supplyOrderId,
        String operationId,
        int segmentIndex,
        String standardResourceId,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        int durationMinutes) {
}
