package com.plantops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 已下发工单的工艺路径与主计划槽位摘要。 */
public record WorkOrderRoutingDetailDto(
        String workOrderNo,
        String productCode,
        BigDecimal quantity,
        String dispatchStatus,
        LocalDateTime dispatchedTs,
        LocalDate plannedSlotDate,
        String plannedShiftId,
        String masterPlanResourceId,
        List<WorkOrderRoutingOperationDto> operations,
        String batchNo) {
}
