package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "planning_scenario", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "scenario_id"
}))
public class PlanningScenarioEntity extends WorkspaceScopedEntity {

    public String scenarioId;
    public String name;
    public boolean isDefault;
    public String strategyId;
    public String ruleSetVersionId;
    public String currentPlanVersionId;
    public String previousPlanVersionId;
    public LocalDateTime createdAt;

    public static PlanningScenarioEntity findByScenarioId(String scenarioId) {
        return find("workspaceId = ?1 and scenarioId = ?2", ws(), scenarioId).firstResult();
    }

    public static List<PlanningScenarioEntity> listInWorkspace() {
        return list("workspaceId = ?1 order by isDefault desc, createdAt asc", ws());
    }

    public static PlanningScenarioEntity findDefault() {
        PlanningScenarioEntity d = find("workspaceId = ?1 and isDefault = true", ws()).firstResult();
        if (d != null) {
            return d;
        }
        return listInWorkspace().stream().findFirst().orElse(null);
    }
}
