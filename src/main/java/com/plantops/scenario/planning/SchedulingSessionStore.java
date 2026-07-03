package com.plantops.scenario.planning;

import com.plantops.scenario.planning.sandbox.OntologySandboxStore;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SchedulingSessionStore extends OntologySandboxStore<SchedulingSession> {

    @Override
    protected String notFoundMessage(String sessionId) {
        return "Schedule session not found: " + sessionId;
    }

    @Override
    protected String expiredMessage(String sessionId) {
        return "Schedule session expired: " + sessionId;
    }
}
