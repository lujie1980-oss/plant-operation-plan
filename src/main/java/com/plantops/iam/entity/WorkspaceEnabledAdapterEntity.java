package com.plantops.iam.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_enabled_adapter")
@IdClass(WorkspaceEnabledAdapterId.class)
public class WorkspaceEnabledAdapterEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "adapter_id", length = 30)
    public String adapterId;

    @Column(nullable = false)
    public boolean enabled;

    public static java.util.List<WorkspaceEnabledAdapterEntity> findByWorkspace(String workspaceId) {
        return list("workspaceId", workspaceId);
    }
}
