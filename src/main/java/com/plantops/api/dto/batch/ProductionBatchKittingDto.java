package com.plantops.api.dto.batch;

import com.plantops.api.dto.WorkOrderKittingLineDto;

import java.math.BigDecimal;
import java.util.List;

/** 待排批次齐套视图：批次为主键，工单为参考属性。 */
public record ProductionBatchKittingDto(
        String batchNo,
        int batchSeq,
        BigDecimal quantity,
        String workOrderNo,
        String productCode,
        BigDecimal workOrderQuantity,
        String kittingStatus,
        boolean pendingScheduleEligible,
        List<WorkOrderKittingLineDto> lines) {
}
