package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "simulation_profile", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "profile_id"
}))
public class SimulationProfileEntity extends WorkspaceScopedEntity {

    @Column(name = "profile_id", nullable = false, length = 64)
    public String profileId;

    @Column(nullable = false, length = 256)
    public String name;

    @Column(nullable = false, length = 32)
    public String layer = "DETAIL_SCHEDULE";

    @Column(name = "master_plan_version_id", length = 64)
    public String masterPlanVersionId;

    @Column(name = "config_json", nullable = false, columnDefinition = "CLOB")
    public String configJson;

    @Column(nullable = false)
    public boolean active;

    @Column(name = "updated_ts", nullable = false)
    public LocalDateTime updatedTs = LocalDateTime.now();

    public static SimulationProfileEntity findByProfileId(String profileId) {
        return find("workspaceId = ?1 and profileId = ?2", ws(), profileId).firstResult();
    }

    public static List<SimulationProfileEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static SimulationProfileEntity findActiveForLayer(String layer, String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            SimulationProfileEntity scoped = find(
                    "workspaceId = ?1 and layer = ?2 and active = true and masterPlanVersionId = ?3",
                    ws(),
                    layer,
                    masterPlanVersionId).firstResult();
            if (scoped != null) {
                return scoped;
            }
        }
        return find(
                "workspaceId = ?1 and layer = ?2 and active = true and masterPlanVersionId is null",
                ws(),
                layer).firstResult();
    }
}
