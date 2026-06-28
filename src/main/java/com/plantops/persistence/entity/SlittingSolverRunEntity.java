package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "slitting_solver_run", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "run_id"}))
public class SlittingSolverRunEntity extends WorkspaceScopedEntity {

    public String runId;
    /** PLAN_SOLVE | STUDIO_OPTIMIZE | SESSION_OPTIMIZE */
    public String runType;
    public String planVersionId;
    public String masterNodeId;
    public String sessionId;
    /** RUNNING | SUCCESS | FAILED */
    public String status;
    public LocalDateTime startedTs;
    public LocalDateTime finishedTs;
    public Long durationMs;
    public String score;
    public String summary;
    public String errorMessage;

    @Lob
    public String executionLog;

    public static SlittingSolverRunEntity findByRunId(String runId) {
        return find("workspaceId = ?1 and runId = ?2", ws(), runId).firstResult();
    }

    public static List<SlittingSolverRunEntity> listRecent(int limit) {
        return find("workspaceId = ?1 order by startedTs desc", ws())
                .page(0, Math.max(1, limit))
                .list();
    }
}
