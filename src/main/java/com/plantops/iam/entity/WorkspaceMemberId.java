package com.plantops.iam.entity;

import java.io.Serializable;
import java.util.Objects;

public class WorkspaceMemberId implements Serializable {
    public String workspaceId;
    public String userId;

    public WorkspaceMemberId() {}

    public WorkspaceMemberId(String workspaceId, String userId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WorkspaceMemberId other)) return false;
        return Objects.equals(workspaceId, other.workspaceId)
                && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId, userId);
    }
}
