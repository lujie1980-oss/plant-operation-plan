package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite PK for P0 ont_* entity tables (workspace + revision + entity). */
public class OntEntityKey implements Serializable {

    public String workspaceId;
    public String revisionId;
    public String entityId;

    public OntEntityKey() {}

    public OntEntityKey(String workspaceId, String revisionId, String entityId) {
        this.workspaceId = workspaceId;
        this.revisionId = revisionId;
        this.entityId = entityId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntEntityKey other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(revisionId, other.revisionId)
                && Objects.equals(entityId, other.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, revisionId, entityId);
    }
}
