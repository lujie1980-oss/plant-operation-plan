package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class OntRevisionHeadId implements Serializable {

    public String workspaceId;
    public String scopeKey;

    public OntRevisionHeadId() {}

    public OntRevisionHeadId(String workspaceId, String scopeKey) {
        this.workspaceId = workspaceId;
        this.scopeKey = scopeKey;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntRevisionHeadId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(scopeKey, other.scopeKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, scopeKey);
    }
}
