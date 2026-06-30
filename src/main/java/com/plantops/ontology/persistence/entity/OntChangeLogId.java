package com.plantops.ontology.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class OntChangeLogId implements Serializable {

    public String workspaceId;
    public String revisionId;
    public long changeSeq;

    public OntChangeLogId() {}

    public OntChangeLogId(String workspaceId, String revisionId, long changeSeq) {
        this.workspaceId = workspaceId;
        this.revisionId = revisionId;
        this.changeSeq = changeSeq;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OntChangeLogId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(revisionId, other.revisionId)
                && changeSeq == other.changeSeq;
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, revisionId, changeSeq);
    }
}
