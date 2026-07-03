package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_enabled_module")
@IdClass(WorkspaceEnabledModuleId.class)
public class WorkspaceEnabledModuleEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "module_id", length = 20)
    public String moduleId;

    @Column(nullable = false)
    public boolean enabled = true;

    public static java.util.List<WorkspaceEnabledModuleEntity> findByWorkspace(String workspaceId) {
        return list("workspaceId", workspaceId);
    }
}
