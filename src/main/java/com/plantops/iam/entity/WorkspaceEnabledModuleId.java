package com.plantops.iam.entity;

import java.io.Serializable;
import java.util.Objects;

public class WorkspaceEnabledModuleId implements Serializable {
    public String workspaceId;
    public String moduleId;

    public WorkspaceEnabledModuleId() {}

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorkspaceEnabledModuleId other)) return false;
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(moduleId, other.moduleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, moduleId);
    }
}
