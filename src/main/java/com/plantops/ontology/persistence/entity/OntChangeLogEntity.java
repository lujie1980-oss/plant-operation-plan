package com.plantops.ontology.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "ont_change_log")
@IdClass(OntChangeLogId.class)
public class OntChangeLogEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "revision_id", length = 128)
    public String revisionId;

    @Id
    @Column(name = "change_seq", nullable = false)
    public long changeSeq;

    @Column(name = "change_type", nullable = false, length = 64)
    public String changeType;

    @Column(name = "entity_type", length = 64)
    public String entityType;

    @Column(name = "entity_id", length = 128)
    public String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    public Map<String, Object> payloadJson = Map.of();

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();
}
