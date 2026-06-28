package com.plantops.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.DetailScheduleResultDto;
import com.plantops.api.dto.MasterPlanResultDto;
import com.plantops.api.dto.PipelineRunLogLineDto;
import com.plantops.api.dto.PlanningPipelineRunDto;
import com.plantops.api.dto.planning.PlanningPipelineRunDiagnosticsDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.persistence.entity.PlanningPipelineRunEntity;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PipelineRunService {

    private static final TypeReference<List<PipelineRunLogLineDto>> LOG_LIST_TYPE = new TypeReference<>() {
    };

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(MasterPlanStrategyConfigService.ResolvedStrategy strategy) {
        return startRun(strategy, null, null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(
            MasterPlanStrategyConfigService.ResolvedStrategy strategy,
            String scenarioId,
            String ruleSetVersionId) {
        PlanningPipelineRunEntity row = new PlanningPipelineRunEntity();
        row.runId = "RUN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        row.strategyId = strategy.id();
        row.strategyName = strategy.name();
        row.capacityStrategy = strategy.capacityStrategy().name();
        row.scenarioId = scenarioId;
        row.ruleSetVersionId = ruleSetVersionId;
        row.status = "RUNNING";
        row.startedTs = LocalDateTime.now();
        row.executionLog = serializeLogs(List.of());
        row.stampWorkspace();
        row.persist();
        appendLog(
                row.runId,
                "INFO",
                "主计划运行已启动，策略：" + strategy.name()
                        + "（产能：" + strategy.capacityStrategy().name() + "）");
        return row.runId;
    }

    /** @deprecated 兼容旧调用 */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(MasterPlanCapacityStrategy strategy) {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved =
                strategyConfigService.resolveFromRequest(null, strategy != null ? strategy.name() : null);
        return startRun(resolved);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(String strategyId) {
        return startRun(strategyId, null, null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(String strategyId, String scenarioId, String ruleSetVersionId) {
        return startRun(strategyConfigService.resolve(strategyId), scenarioId, ruleSetVersionId);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void appendLog(String runId, String level, String message) {
        PlanningPipelineRunEntity row = PlanningPipelineRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        List<PipelineRunLogLineDto> lines = parseLogs(row.executionLog);
        lines.add(new PipelineRunLogLineDto(LocalDateTime.now().toString(), level, message));
        row.executionLog = serializeLogs(lines);
        row.persist();
    }

    public PlanningPipelineRunDto getRun(String runId) {
        PlanningPipelineRunEntity row = PlanningPipelineRunEntity.findByRunId(runId);
        return row != null ? toDto(row) : null;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void completeSuccess(
            String runId,
            MasterPlanResultDto masterPlan,
            DetailScheduleResultDto detailSchedule) {
        PlanningPipelineRunEntity row = PlanningPipelineRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        LocalDateTime finished = LocalDateTime.now();
        row.status = "SUCCESS";
        row.finishedTs = finished;
        row.durationMs = java.time.Duration.between(row.startedTs, finished).toMillis();
        row.masterPlanVersionId = masterPlan != null ? masterPlan.planVersionId() : null;
        row.detailPlanVersionId = detailSchedule != null ? detailSchedule.planVersionId() : null;
        row.masterPlanScore = masterPlan != null ? masterPlan.score() : null;
        row.errorMessage = null;
        row.persist();
        appendLog(runId, "INFO", "主计划运行成功完成，耗时 " + (row.durationMs / 1000) + " 秒");
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void completeFailure(String runId, String errorMessage) {
        PlanningPipelineRunEntity row = PlanningPipelineRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        LocalDateTime finished = LocalDateTime.now();
        row.status = "FAILED";
        row.finishedTs = finished;
        row.durationMs = java.time.Duration.between(row.startedTs, finished).toMillis();
        row.errorMessage = errorMessage != null && errorMessage.length() > 2000
                ? errorMessage.substring(0, 2000)
                : errorMessage;
        row.persist();
        appendLog(runId, "ERROR", "计划运行失败：" + (errorMessage != null ? errorMessage : "未知错误"));
    }

    public List<PlanningPipelineRunDto> listRecent(int limit) {
        return PlanningPipelineRunEntity.listRecent(limit).stream()
                .map(this::toDto)
                .toList();
    }

    private PlanningPipelineRunDto toDto(PlanningPipelineRunEntity e) {
        return new PlanningPipelineRunDto(
                e.runId,
                e.capacityStrategy,
                e.strategyId,
                e.strategyName,
                e.status,
                e.startedTs != null ? e.startedTs.toString() : null,
                e.finishedTs != null ? e.finishedTs.toString() : null,
                e.durationMs,
                e.masterPlanVersionId,
                e.detailPlanVersionId,
                e.masterPlanScore,
                e.errorMessage,
                parseLogs(e.executionLog),
                parseDiagnostics(e.diagnosticsJson));
    }

    private PlanningPipelineRunDiagnosticsDto parseDiagnostics(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PlanningPipelineRunDiagnosticsDto.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeLogs(List<PipelineRunLogLineDto> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<PipelineRunLogLineDto> parseLogs(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, LOG_LIST_TYPE);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
