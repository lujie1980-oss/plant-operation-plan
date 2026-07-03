package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class OntSessionId implements Serializable {

    public String workspaceId;
    public String sessionId;

    public OntSessionId() {}

    public OntSessionId(String workspaceId, String sessionId) {
        this.workspaceId = workspaceId;
        this.sessionId = sessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntSessionId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(sessionId, other.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, sessionId);
    }
}
