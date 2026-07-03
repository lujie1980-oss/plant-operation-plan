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
@Table(name = "ont_revision")
@IdClass(OntRevisionId.class)
public class OntRevisionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "revision_id", length = 128)
    public String revisionId;

    @Column(name = "parent_revision_id", length = 128)
    public String parentRevisionId;

    @Column(name = "plan_version_id", length = 128)
    public String planVersionId;

    @Column(name = "session_id", length = 128)
    public String sessionId;

    @Column(nullable = false, length = 16)
    public String status;

    @Column(name = "persistence_mode", nullable = false, length = 16)
    public String persistenceMode = "FULL";

    @Column(name = "change_seq", nullable = false)
    public long changeSeq;

    @Column(name = "committed_at")
    public LocalDateTime committedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public static Optional<OntRevisionEntity> findRevision(String workspaceId, String revisionId) {
        return find("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId)
                .firstResultOptional();
    }
}
