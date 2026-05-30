package com.plantops.api.dto;

import java.math.BigDecimal;

public record CapacityBucketWorkOrderDto(
        String workOrderNo,
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        BigDecimal quantity,
        int loadMinutes,
        String scheduleSource,
        boolean feedbackLocked
) {

    public CapacityBucketWorkOrderDto(
            String workOrderNo,
            String salesOrderNo,
            int salesOrderLineNo,
            String productCode,
            BigDecimal quantity,
            int loadMinutes,
            String scheduleSource) {
        this(workOrderNo, salesOrderNo, salesOrderLineNo, productCode, quantity, loadMinutes, scheduleSource, false);
    }
}
