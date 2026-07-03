package com.plantops.scenario.slitting;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SlittingSessionStore {

    private static final java.time.Duration DEFAULT_TTL = java.time.Duration.ofHours(8);

    private final Map<String, SlittingSession> sessions = new ConcurrentHashMap<>();

    public SlittingSession put(SlittingSession session) {
        sessions.put(session.sessionId(), session);
        return session;
    }

    public SlittingSession require(String sessionId) {
        SlittingSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("slitting session not found: " + sessionId);
        }
        if (session.expired(LocalDateTime.now())) {
            sessions.remove(sessionId);
            throw new NotFoundException("slitting session expired: " + sessionId);
        }
        return session;
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public LocalDateTime defaultExpiresAt(LocalDateTime createdAt) {
        return createdAt.plus(DEFAULT_TTL);
    }
}
