package com.plantops.api.dto;

import java.time.LocalDate;

/** 设备组 × 日历日 × 班次 产能格（由 leaf {@link com.plantops.ontology.period.StandardResourcePeriod} 投影）。 */
public record SrpCapacityCellDto(
        String resourceId,
        LocalDate date,
        String shiftId,
        int availableMinutes,
        int reservedMinutes,
        int utilizationPct,
        boolean overloaded) {
}
