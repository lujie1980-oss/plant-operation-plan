package com.plantops.api.dto;

import java.util.List;

public record DetailScheduleResultDto(
        String planVersionId,
        String score,
        Long solveDurationMs,
        List<DetailScheduleOperationDto> operations,
        List<ShortageRecommendationDto> shortageRecommendations,
        /** 排程后滚动刷新主计划时返回；否则为 null */
        MasterPlanRefreshResultDto masterPlanRefresh
) {

    public DetailScheduleResultDto(
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<DetailScheduleOperationDto> operations,
            List<ShortageRecommendationDto> shortageRecommendations) {
        this(planVersionId, score, solveDurationMs, operations, shortageRecommendations, null);
    }
}
