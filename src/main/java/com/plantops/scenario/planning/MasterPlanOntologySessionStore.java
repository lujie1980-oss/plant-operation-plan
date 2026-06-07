package com.plantops.scenario.planning;

import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MasterPlanOntologySessionStore {

    private static final Duration DEFAULT_TTL = Duration.ofHours(8);

    private final Map<String, MasterPlanOntologySession> sessions = new ConcurrentHashMap<>();

    public MasterPlanOntologySession put(MasterPlanOntologySession session) {
        sessions.put(session.sessionId(), session);
        return session;
    }

    public MasterPlanOntologySession require(String sessionId) {
        return require(sessionId, WorkspaceResolver.currentWorkspaceId());
    }

    public MasterPlanOntologySession require(String sessionId, String workspaceId) {
        MasterPlanOntologySession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Master plan session not found: " + sessionId);
        }
        if (!session.workspaceId().equals(workspaceId)) {
            throw new NotFoundException("Master plan session not found: " + sessionId);
        }
        if (session.expired(LocalDateTime.now())) {
            sessions.remove(sessionId);
            throw new NotFoundException("Master plan session expired: " + sessionId);
        }
        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public LocalDateTime defaultExpiresAt(LocalDateTime createdAt) {
        return createdAt.plus(DEFAULT_TTL);
    }

    int size() {
        return sessions.size();
    }
}
