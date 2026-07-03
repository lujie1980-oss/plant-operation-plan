package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class OntEntityPolicyId implements Serializable {

    public String workspaceId;
    public String entityKind;

    public OntEntityPolicyId() {}

    public OntEntityPolicyId(String workspaceId, String entityKind) {
        this.workspaceId = workspaceId;
        this.entityKind = entityKind;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntEntityPolicyId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(entityKind, other.entityKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, entityKind);
    }
}
