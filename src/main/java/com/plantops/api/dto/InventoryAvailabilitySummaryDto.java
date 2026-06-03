package com.plantops.api.dto;

import java.math.BigDecimal;

/** 按料号汇总的可用库存。 */
public record InventoryAvailabilitySummaryDto(
        String productCode,
        BigDecimal totalOnhand,
        BigDecimal totalAvailable,
        int stockingPointCount) {
}
