package com.plantops.persistence.entity;

import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class WorkspaceScopedEntity extends PanacheEntity {

    @Column(name = "workspace_id", nullable = false, length = 64)
    public String workspaceId;

    public static String ws() {
        return WorkspaceResolver.currentWorkspaceId();
    }

    public void stampWorkspace() {
        this.workspaceId = ws();
    }

    public void ensureWorkspace() {
        if (workspaceId == null || workspaceId.isBlank()) {
            stampWorkspace();
        }
    }
}
