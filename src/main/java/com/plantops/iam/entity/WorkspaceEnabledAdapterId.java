package com.plantops.iam.entity;

import java.io.Serializable;
import java.util.Objects;

public class WorkspaceEnabledAdapterId implements Serializable {
    public String workspaceId;
    public String adapterId;

    public WorkspaceEnabledAdapterId() {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorkspaceEnabledAdapterId other)) return false;
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(adapterId, other.adapterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, adapterId);
    }
}
