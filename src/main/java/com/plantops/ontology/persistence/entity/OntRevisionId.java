package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class OntRevisionId implements Serializable {

    public String workspaceId;
    public String revisionId;

    public OntRevisionId() {}

    public OntRevisionId(String workspaceId, String revisionId) {
        this.workspaceId = workspaceId;
        this.revisionId = revisionId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntRevisionId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(revisionId, other.revisionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, revisionId);
    }
}
