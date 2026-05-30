package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "planning_pipeline_run", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "run_id"
}))
public class PlanningPipelineRunEntity extends WorkspaceScopedEntity {

    public String runId;
    public String capacityStrategy;
    public String strategyId;
    public String strategyName;
    /** RUNNING | SUCCESS | FAILED */
    public String status;
    public LocalDateTime startedTs;
    public LocalDateTime finishedTs;
    public Long durationMs;
    public String masterPlanVersionId;
    public String detailPlanVersionId;
    public String masterPlanScore;
    public String errorMessage;
    /** JSON 数组：运行关键日志 */
    @Lob
    public String executionLog;

    /** JSON：{@link com.plantops.api.dto.planning.PlanningPipelineRunDiagnosticsDto} */
    @Lob
    public String diagnosticsJson;

    public String scenarioId;

    public String ruleSetVersionId;

    public static PlanningPipelineRunEntity findByRunId(String runId) {
        return find("workspaceId = ?1 and runId = ?2", ws(), runId).firstResult();
    }

    public static List<PlanningPipelineRunEntity> listRecent(int limit) {
        return find("workspaceId = ?1 order by startedTs desc", ws()).page(0, Math.max(1, limit)).list();
    }
}
