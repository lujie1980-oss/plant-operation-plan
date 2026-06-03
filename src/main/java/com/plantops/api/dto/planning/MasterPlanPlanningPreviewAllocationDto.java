package com.plantops.api.dto.planning;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 推演层分配候选 + 可选求解后的槽位（与 {@link com.plantops.api.dto.MasterPlanAllocationDto} 对齐）。 */
public record MasterPlanPlanningPreviewAllocationDto(
        String allocationId,
        int segmentIndex,
        String workOrderNo,
        String productCode,
        String resourceId,
        int operationSeq,
        String operationName,
        LocalDate dueDate,
        int durationMinutes,
        boolean scheduled,
        Integer slotIndex,
        LocalDate slotDate,
        String shiftId,
        LocalDateTime plannedStartTs,
        LocalDateTime plannedEndTs) {
}
