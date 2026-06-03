package com.plantops.scenario.planning;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SchedulingSessionStore {

    private static final java.time.Duration DEFAULT_TTL = java.time.Duration.ofHours(8);

    private final Map<String, SchedulingSession> sessions = new ConcurrentHashMap<>();

    public SchedulingSession put(SchedulingSession session) {
        sessions.put(session.sessionId(), session);
        return session;
    }

    public SchedulingSession require(String sessionId) {
        SchedulingSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Schedule session not found: " + sessionId);
        }
        if (session.expired(LocalDateTime.now())) {
            sessions.remove(sessionId);
            throw new NotFoundException("Schedule session expired: " + sessionId);
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
