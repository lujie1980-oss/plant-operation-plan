package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "slitting_plan_version", uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "plan_version_id"}))
public class SlittingPlanVersionEntity extends WorkspaceScopedEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SOLVED = "SOLVED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Column(name = "plan_version_id", nullable = false, length = 64)
    public String planVersionId;

    @Column(name = "name", length = 256)
    public String name;

    @Column(name = "status", nullable = false, length = 32)
    public String status = STATUS_DRAFT;

    @Column(name = "score", length = 64)
    public String score;

    @Column(name = "utilization_pct")
    public BigDecimal utilizationPct;

    @Column(name = "solve_duration_ms")
    public Long solveDurationMs;

    @Column(name = "solver_phase", length = 32)
    public String solverPhase;

    @Column(name = "created_ts", nullable = false)
    public LocalDateTime createdTs = LocalDateTime.now();

    public static SlittingPlanVersionEntity findByPlanVersionId(String planVersionId) {
        return find("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId).firstResult();
    }

    public static List<SlittingPlanVersionEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by createdTs desc", ws());
    }
}
