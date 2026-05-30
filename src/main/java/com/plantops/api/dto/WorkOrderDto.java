package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkOrderDto(
        Long id,
        String workOrderNo,
        String parentWorkOrderNo,
        String workOrderSource,
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        BigDecimal quantity,
        String resourceId,
        int sequenceNo,
        String dispatchStatus,
        LocalDateTime dispatchedTs,
        LocalDate plannedSlotDate,
        String plannedShiftId,
        boolean inScenarioPlan,
        boolean hasScheduleFeedback,
        boolean hasFrozenScheduleFeedback,
        int scheduleFeedbackOperationCount,
        String linkedDetailScheduleVersionId,
        LocalDate needDate,
        int bomLevel,
        int peggingCount,
        WorkOrderTimingWindowDto timingWindow
) {

    public WorkOrderDto(
            Long id,
            String workOrderNo,
            String parentWorkOrderNo,
            String workOrderSource,
            String salesOrderNo,
            int salesOrderLineNo,
            String productCode,
            BigDecimal quantity,
            String resourceId,
            int sequenceNo,
            String dispatchStatus,
            LocalDateTime dispatchedTs,
            LocalDate plannedSlotDate,
            String plannedShiftId,
            boolean inScenarioPlan) {
        this(
                id,
                workOrderNo,
                parentWorkOrderNo,
                workOrderSource,
                salesOrderNo,
                salesOrderLineNo,
                productCode,
                quantity,
                resourceId,
                sequenceNo,
                dispatchStatus,
                dispatchedTs,
                plannedSlotDate,
                plannedShiftId,
                inScenarioPlan,
                false,
                false,
                0,
                null,
                null,
                0,
                0,
                null);
    }
}
