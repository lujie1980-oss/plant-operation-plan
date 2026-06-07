package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.rol.RolEngine;

import java.time.LocalDateTime;

public final class MasterPlanOntologySession {

    private final String sessionId;
    private final String workspaceId;
    private final String basePlanVersionId;
    private final OntologyGraph graph;
    private final RolEngine rolEngine;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;

    public MasterPlanOntologySession(
            String sessionId,
            String workspaceId,
            String basePlanVersionId,
            OntologyGraph graph,
            RolEngine rolEngine,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.workspaceId = workspaceId;
        this.basePlanVersionId = basePlanVersionId;
        this.graph = graph;
        this.rolEngine = rolEngine;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String sessionId() {
        return sessionId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String basePlanVersionId() {
        return basePlanVersionId;
    }

    public OntologyGraph graph() {
        return graph;
    }

    public RolEngine rolEngine() {
        return rolEngine;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public boolean expired() {
        return expired(LocalDateTime.now());
    }

    public boolean expired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
