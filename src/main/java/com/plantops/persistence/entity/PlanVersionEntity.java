package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.time.LocalDateTime;



@Entity

@Table(name = "plan_version", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "plan_version_id"

}))

public class PlanVersionEntity extends WorkspaceScopedEntity {



    public String planVersionId;

    public String planType;

    public LocalDateTime planGeneratedTs;

    public String changedBy;

    public String changeSource;

    public Long solveDurationMs;

    public String score;

    /** 主计划产能策略：UNCONSTRAINED | FINITE_CAPACITY */

    public String capacityStrategy;

    public String strategyId;

    public String strategyName;

    public String parentPlanVersionId;

    public String sourceDetailScheduleVersionId;

    public String scenarioId;

    /** CURRENT | PREVIOUS | ARCHIVED */
    public String versionStatus;

    public static PlanVersionEntity findByVersionId(String planVersionId) {

        return find("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId).firstResult();

    }

    public static java.util.List<PlanVersionEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}


