package com.plantops.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "workspace")
public class WorkspaceEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Column(nullable = false, length = 128)
    public String name;

    @Column(length = 512)
    public String description;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_default", nullable = false)
    public boolean isDefault;

    @Column(name = "default_scenario_id", length = 32)
    public String defaultScenarioId;

    public static WorkspaceEntity findByWorkspaceId(String workspaceId) {
        return findById(workspaceId);
    }

    public static boolean existsById(String workspaceId) {
        return count("workspaceId", workspaceId) > 0;
    }

    public static WorkspaceEntity findDefault() {
        WorkspaceEntity d = find("isDefault", true).firstResult();
        if (d != null) {
            return d;
        }
        return findById(com.plantops.workspace.WorkspaceConstants.DEFAULT_ID);
    }

    public static List<WorkspaceEntity> listAllOrdered() {
        return list("order by isDefault desc, createdAt asc");
    }
}
