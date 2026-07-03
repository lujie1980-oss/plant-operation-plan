package com.plantops.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class WorkspaceAdapterConfigId implements Serializable {
    public String workspaceId;
    public String adapterId;

    public WorkspaceAdapterConfigId() {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorkspaceAdapterConfigId other)) {
            return false;
        }
        return Objects.equals(workspaceId, other.workspaceId) && Objects.equals(adapterId, other.adapterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, adapterId);
    }
}
