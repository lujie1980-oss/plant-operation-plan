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
@Table(name = "ont_entity_policy")
@IdClass(OntEntityPolicyId.class)
public class OntEntityPolicyEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "entity_kind", length = 64)
    public String entityKind;

    @Column(name = "storage", nullable = false, length = 16)
    public String storage;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public static Optional<OntEntityPolicyEntity> findPolicy(String workspaceId, String entityKind) {
        return find("workspaceId = ?1 and entityKind = ?2", workspaceId, entityKind)
                .firstResultOptional();
    }
}
