package com.plantops.scenario.slitting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.PipelineRunLogLineDto;
import com.plantops.api.dto.slitting.SlittingSolverRunDto;
import com.plantops.persistence.entity.SlittingSolverRunEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SlittingSolverRunService {

    public static final String TYPE_PLAN_SOLVE = "PLAN_SOLVE";
    public static final String TYPE_STUDIO_OPTIMIZE = "STUDIO_OPTIMIZE";
    public static final String TYPE_SESSION_OPTIMIZE = "SESSION_OPTIMIZE";

    private static final TypeReference<List<PipelineRunLogLineDto>> LOG_LIST_TYPE = new TypeReference<>() {
    };

    @Inject
    ObjectMapper objectMapper;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String startRun(
            String runType,
            String planVersionId,
            String masterNodeId,
            String sessionId,
            String openingMessage) {
        SlittingSolverRunEntity row = new SlittingSolverRunEntity();
        row.runId = "SLR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        row.runType = runType;
        row.planVersionId = planVersionId;
        row.masterNodeId = masterNodeId;
        row.sessionId = sessionId;
        row.status = "RUNNING";
        row.startedTs = LocalDateTime.now();
        row.executionLog = serializeLogs(List.of());
        row.stampWorkspace();
        row.persist();
        if (openingMessage != null && !openingMessage.isBlank()) {
            appendLog(row.runId, "INFO", openingMessage);
        }
        return row.runId;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void appendLog(String runId, String level, String message) {
        if (runId == null || message == null || message.isBlank()) {
            return;
        }
        SlittingSolverRunEntity row = SlittingSolverRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        List<PipelineRunLogLineDto> lines = parseLogs(row.executionLog);
        lines.add(new PipelineRunLogLineDto(LocalDateTime.now().toString(), level, message.trim()));
        row.executionLog = serializeLogs(lines);
        row.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void finishSuccess(String runId, long durationMs, String score, String summary) {
        SlittingSolverRunEntity row = SlittingSolverRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        row.status = "SUCCESS";
        row.finishedTs = LocalDateTime.now();
        row.durationMs = durationMs;
        row.score = score;
        row.summary = summary;
        row.persist();
        appendLog(runId, "INFO", "运行完成" + (summary != null ? "：" + summary : ""));
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void finishFailed(String runId, long durationMs, String errorMessage) {
        SlittingSolverRunEntity row = SlittingSolverRunEntity.findByRunId(runId);
        if (row == null) {
            return;
        }
        row.status = "FAILED";
        row.finishedTs = LocalDateTime.now();
        row.durationMs = durationMs;
        row.errorMessage = errorMessage;
        row.persist();
        appendLog(runId, "ERROR", errorMessage != null ? errorMessage : "运行失败");
    }

    public SlittingSolverRunDto getRun(String runId) {
        SlittingSolverRunEntity row = SlittingSolverRunEntity.findByRunId(runId);
        return row != null ? toDto(row) : null;
    }

    public List<SlittingSolverRunDto> listRecent(int limit) {
        return SlittingSolverRunEntity.listRecent(limit).stream().map(this::toDto).toList();
    }

    private SlittingSolverRunDto toDto(SlittingSolverRunEntity e) {
        return new SlittingSolverRunDto(
                e.runId,
                e.runType,
                e.planVersionId,
                e.masterNodeId,
                e.sessionId,
                e.status,
                e.startedTs != null ? e.startedTs.toString() : null,
                e.finishedTs != null ? e.finishedTs.toString() : null,
                e.durationMs,
                e.score,
                e.summary,
                e.errorMessage,
                parseLogs(e.executionLog));
    }

    private String serializeLogs(List<PipelineRunLogLineDto> lines) {
        try {
            return objectMapper.writeValueAsString(lines != null ? lines : List.of());
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<PipelineRunLogLineDto> parseLogs(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, LOG_LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }
}
