package com.plantops.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_adapter_config")
@IdClass(WorkspaceAdapterConfigId.class)
public class WorkspaceAdapterConfigEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "adapter_id", length = 30)
    public String adapterId;

    @Column(name = "config_json", length = 4000)
    public String configJson;

    @Column(nullable = false)
    public boolean configured;

    @Column(name = "last_run_at")
    public LocalDateTime lastRunAt;

    @Column(name = "last_status", length = 32)
    public String lastStatus;

    @Column(name = "last_message", length = 512)
    public String lastMessage;

    public static WorkspaceAdapterConfigEntity findByKey(String workspaceId, String adapterId) {
        return find("workspaceId = ?1 and adapterId = ?2", workspaceId, adapterId).firstResult();
    }
}
