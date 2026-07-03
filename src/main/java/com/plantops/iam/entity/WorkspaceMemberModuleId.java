package com.plantops.iam.entity;

import java.io.Serializable;
import java.util.Objects;

public class WorkspaceMemberModuleId implements Serializable {

    public String workspaceId;
    public String userId;
    public String moduleId;

    public WorkspaceMemberModuleId() {}

    public WorkspaceMemberModuleId(String workspaceId, String userId, String moduleId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.moduleId = moduleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMemberModuleId that)) return false;
        return Objects.equals(workspaceId, that.workspaceId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(moduleId, that.moduleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, userId, moduleId);
    }
}
