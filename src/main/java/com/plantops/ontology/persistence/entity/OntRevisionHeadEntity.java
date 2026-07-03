package com.plantops.ontology.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "ont_revision_head")
@IdClass(OntRevisionHeadId.class)
public class OntRevisionHeadEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "scope_key", length = 256)
    public String scopeKey;

    @Column(name = "revision_id", nullable = false, length = 128)
    public String revisionId;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public static Optional<OntRevisionHeadEntity> findHead(String workspaceId, String scopeKey) {
        return find("workspaceId = ?1 and scopeKey = ?2", workspaceId, scopeKey)
                .firstResultOptional();
    }
}
