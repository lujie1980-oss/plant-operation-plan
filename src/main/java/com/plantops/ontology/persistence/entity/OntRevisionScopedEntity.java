package com.plantops.ontology.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

@MappedSuperclass
@IdClass(OntEntityKey.class)
public abstract class OntRevisionScopedEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "revision_id", length = 128)
    public String revisionId;

    @Id
    @Column(name = "entity_id", length = 128)
    public String entityId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public void stampKeys(String workspaceId, String revisionId, String entityId) {
        this.workspaceId = workspaceId;
        this.revisionId = revisionId;
        this.entityId = entityId;
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }
}
