package com.plantops.api.dto;

import java.util.List;

/** 排程页 KPI：可选传入当前推演工序（Session 预览），否则按已发布版本计算。 */
public record DetailSchedulePageKpisRequestDto(
        String detailScheduleVersionId,
        List<DetailScheduleOperationDto> operations) {
}
