package com.plantops.iam.context;

import com.plantops.iam.entity.WorkspaceMemberEntity;
import jakarta.enterprise.context.RequestScoped;

/**
 * 请求级安全上下文。M1 由 AuthenticationFilter 填充；与 WorkspaceContext 共存。
 */
@RequestScoped
public class SecurityContext {

    private String currentUserId;
    private String displayName;
    private boolean superAdmin;
    private boolean devMode;
    private String workspaceRole;

    // ---- setters (by filters) ----

    public void setDevUser() {
        this.currentUserId = "dev";
        this.displayName = "Dev";
        this.superAdmin = true;
        this.devMode = true;
    }

    public void setCurrentUserId(String userId) { this.currentUserId = userId; }
    public void setDisplayName(String name) { this.displayName = name; }
    public void setSuperAdmin(boolean v) { this.superAdmin = v; }
    public void setDevMode(boolean v) { this.devMode = v; }

    public void setWorkspaceRoleFrom(WorkspaceMemberEntity member) {
        this.workspaceRole = member != null ? member.role : null;
    }

    public void setWorkspaceRole(String role) { this.workspaceRole = role; }

    // ---- getters ----

    public String getCurrentUserId() { return currentUserId; }
    public String getDisplayName() { return displayName; }
    public boolean isSuperAdmin() { return superAdmin; }
    public boolean isDevMode() { return devMode; }
    public boolean isAuthenticated() { return currentUserId != null; }
    public String getWorkspaceRole() { return workspaceRole; }

    // ---- convenience ----

    /** OWNER or WS_ADMIN */
    public boolean isWorkspaceAdmin() {
        return "OWNER".equals(workspaceRole) || "WS_ADMIN".equals(workspaceRole);
    }

    public boolean isWorkspaceOwner() {
        return "OWNER".equals(workspaceRole);
    }

    public void clear() {
        this.currentUserId = null;
        this.displayName = null;
        this.superAdmin = false;
        this.devMode = false;
        this.workspaceRole = null;
    }
}
