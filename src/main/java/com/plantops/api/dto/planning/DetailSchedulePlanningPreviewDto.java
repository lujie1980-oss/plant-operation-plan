package com.plantops.api.dto.planning;

import com.plantops.api.dto.ShortageRecommendationDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 细排程推演层统一响应：诊断 + 产线/工序快照；{@code solve=true} 时含选优结果（反写到同一批工序对象）。
 */
public record DetailSchedulePlanningPreviewDto(
        LocalDateTime computedAt,
        LocalDate planningAnchor,
        String masterPlanVersionId,
        boolean solved,
        boolean persisted,
        boolean initialQueuesSeeded,
        String planVersionId,
        String score,
        Long solveDurationMs,
        DetailSchedulePlanningDiagnosticsDto diagnostics,
        List<DetailSchedulePlanningPreviewLineDto> lines,
        List<DetailSchedulePlanningPreviewOperationDto> operations,
        int operationCount,
        int scheduledOperationCount,
        List<ShortageRecommendationDto> shortageRecommendations,
        List<ScheduleConstraintViolationDto> violations,
        String simulationMode,
        Long simulationDurationMs,
        List<String> recalculatedOperationIds) {
}
