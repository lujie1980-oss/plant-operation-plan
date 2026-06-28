package com.plantops.api.dto;

import java.time.LocalDate;
import java.util.List;

/** 本体 SRP 驱动的产能甘特数据（计划期固定起止 + 日粒度格）。 */
public record SrpCapacityGanttDto(
        LocalDate horizonStart,
        LocalDate horizonEnd,
        List<SrpCapacityCellDto> cells) {
}
