package com.plantops.scenario.planning;

import com.plantops.scenario.planning.sandbox.OntologySandboxStore;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MasterPlanOntologySessionStore extends OntologySandboxStore<MasterPlanOntologySession> {

    @Override
    protected String notFoundMessage(String sessionId) {
        return "Master plan session not found: " + sessionId;
    }

    @Override
    protected String expiredMessage(String sessionId) {
        return "Master plan session expired: " + sessionId;
    }
}
