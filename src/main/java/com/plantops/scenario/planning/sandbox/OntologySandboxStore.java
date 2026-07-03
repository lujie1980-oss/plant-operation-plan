package com.plantops.scenario.planning.sandbox;

import com.plantops.workspace.WorkspaceResolver;
import jakarta.ws.rs.NotFoundException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙盘会话存储基类：工作区校验、TTL 过期清理、默认 8 小时有效期。
 */
public abstract class OntologySandboxStore<S extends OntologySandbox> {

    private static final Duration DEFAULT_TTL = Duration.ofHours(8);

    private final Map<String, S> sessions = new ConcurrentHashMap<>();

    protected abstract String notFoundMessage(String sessionId);

    protected abstract String expiredMessage(String sessionId);

    public S put(S session) {
        sessions.put(session.sessionId(), session);
        return session;
    }

    public S require(String sessionId) {
        return require(sessionId, WorkspaceResolver.currentWorkspaceId());
    }

    public S require(String sessionId, String workspaceId) {
        S session = sessions.get(sessionId);
        if (session == null || !session.workspaceId().equals(workspaceId)) {
            throw new NotFoundException(notFoundMessage(sessionId));
        }
        if (session.expired(LocalDateTime.now())) {
            sessions.remove(sessionId);
            throw new NotFoundException(expiredMessage(sessionId));
        }
        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public LocalDateTime defaultExpiresAt(LocalDateTime createdAt) {
        return createdAt.plus(DEFAULT_TTL);
    }

    public int size() {
        return sessions.size();
    }
}
