package com.plantops.iam.service;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.dto.WorkspaceMembershipDto;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class IamService {

    @Inject
    SecurityContext securityContext;

    public CurrentUser currentUser() {
        String userId = securityContext.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Not authenticated");
        }
        List<WorkspaceEntity> allWs = WorkspaceEntity.listAllOrdered();
        List<WorkspaceMembershipDto> memberships = allWs.stream()
                .filter(ws -> {
                    // dev-mode: 返回所有 WS
                    if (securityContext.isDevMode()) return true;
                    // prod: 按 workspace_member 过滤
                    return WorkspaceMemberEntity.count(
                            "workspaceId = ?1 and userId = ?2", ws.workspaceId, userId) > 0;
                })
                .map(ws -> {
                    WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                            "workspaceId = ?1 and userId = ?2", ws.workspaceId, userId).firstResult();
                    String role = member != null ? member.role : (securityContext.isDevMode() ? "OWNER" : null);
                    List<String> enabledModules = WorkspaceEnabledModuleEntity
                            .findByWorkspace(ws.workspaceId).stream()
                            .filter(m -> m.enabled)
                            .map(m -> m.moduleId)
                            .toList();
                    return new WorkspaceMembershipDto(ws.workspaceId, ws.name, role, enabledModules);
                })
                .toList();

        boolean hasWorkspaces = !memberships.isEmpty();

        return new CurrentUser(userId, securityContext.getDisplayName(),
                securityContext.isSuperAdmin(), hasWorkspaces, memberships);
    }

    public List<WorkspaceMembershipDto> workspaceMemberships() {
        return currentUser().workspaces();
    }

    public record CurrentUser(
            String userId,
            String displayName,
            boolean isSuperAdmin,
            boolean hasWorkspaces,
            List<WorkspaceMembershipDto> workspaces) {
    }
}
