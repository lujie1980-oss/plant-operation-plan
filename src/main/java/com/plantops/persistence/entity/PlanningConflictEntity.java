package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "planning_conflict", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "conflict_id"
}))
public class PlanningConflictEntity extends WorkspaceScopedEntity {

    public String conflictId;
    public String stepId;
    public String planVersionId;
    public String reasonCode;
    public String message;
    public LocalDateTime detectedTs = LocalDateTime.now();
    public boolean resolved;

    public static List<PlanningConflictEntity> listUnresolvedForPlan(String planVersionId) {
        return list(
                "workspaceId = ?1 and planVersionId = ?2 and resolved = false order by detectedTs desc",
                ws(),
                planVersionId);
    }
}
