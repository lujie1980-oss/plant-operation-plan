package com.plantops.api.dto;

/** 待排工单：用户设定是否可排产。 */
public record WorkOrderPendingScheduleEligibleRequestDto(boolean pendingScheduleEligible) {
}
