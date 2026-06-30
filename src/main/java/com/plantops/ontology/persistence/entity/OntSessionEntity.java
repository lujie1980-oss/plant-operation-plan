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
@Table(name = "ont_session")
@IdClass(OntSessionId.class)
public class OntSessionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "workspace_id", length = 64)
    public String workspaceId;

    @Id
    @Column(name = "session_id", length = 128)
    public String sessionId;

    @Column(name = "draft_revision_id", nullable = false, length = 128)
    public String draftRevisionId;

    @Column(name = "base_revision_id", length = 128)
    public String baseRevisionId;

    @Column(name = "delivery_id", length = 128)
    public String deliveryId;

    @Column(name = "trial_revision", nullable = false)
    public int trialRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "solve_profile_json")
    public Map<String, Object> solveProfileJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "optimizer_result_json")
    public Map<String, Object> optimizerResultJson;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    public static String sessionScopeKey(String sessionId) {
        return "SESSION:" + sessionId;
    }

    public static java.util.Optional<OntSessionEntity> findSession(String workspaceId, String sessionId) {
        return find("workspaceId = ?1 and sessionId = ?2", workspaceId, sessionId)
                .firstResultOptional();
    }
}
