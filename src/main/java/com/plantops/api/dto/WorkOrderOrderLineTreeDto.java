package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

/** 某销售订单行视角下的工单 BOM 树（扁平列表 + 订单内父子关系）。 */
public record WorkOrderOrderLineTreeDto(
        String salesOrderNo,
        int salesOrderLineNo,
        String productCode,
        LocalDate dueDate,
        List<OrderLineWorkOrderDto> workOrders) {
}
