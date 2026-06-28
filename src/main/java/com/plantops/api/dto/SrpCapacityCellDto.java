package com.plantops.api.dto;

import java.time.LocalDate;

/** 设备组 × 日历日 产能格（由 {@link com.plantops.ontology.period.StandardResourcePeriod} 展开）。 */
public record SrpCapacityCellDto(
        String resourceId,
        LocalDate date,
        int availableMinutes,
        int reservedMinutes,
        int utilizationPct,
        boolean overloaded) {
}
