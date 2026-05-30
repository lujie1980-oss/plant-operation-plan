package com.plantops.workspace;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class WorkspaceContext {

    private String workspaceId = WorkspaceConstants.DEFAULT_ID;

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String requireWorkspaceId() {
        return workspaceId;
    }
}
