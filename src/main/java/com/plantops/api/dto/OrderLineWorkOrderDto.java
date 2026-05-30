package com.plantops.api.dto;

import java.math.BigDecimal;

/** 订单行视角工单节点：含该行 pegging 量与树内父工单号。 */
public record OrderLineWorkOrderDto(
        WorkOrderDto workOrder,
        String orderLineTreeParentWorkOrderNo,
        BigDecimal peggedQtyForLine) {
}
