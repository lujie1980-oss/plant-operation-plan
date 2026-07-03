package com.plantops.iam.service;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.iam.entity.WorkspaceMemberModuleEntity;
import com.plantops.iam.module.ModuleAuthorizationService;
import com.plantops.iam.module.WorkspaceModuleCatalog;
import com.plantops.iam.module.WorkspaceModuleCatalog.MatchMode;
import com.plantops.iam.module.WorkspaceModuleCatalog.PathRule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class MemberPermissionService {

    public static final String NONE = "NONE";
    public static final String VIEW = "VIEW";
    public static final String EDIT = "EDIT";

    @Inject
    SecurityContext securityContext;

    @Inject
    ModuleAuthorizationService moduleAuthorizationService;

    public String accessLevel(String workspaceId, String userId, String moduleId) {
        if (securityContext.isSuperAdmin()) {
            return EDIT;
        }
        WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                "workspaceId = ?1 and userId = ?2", workspaceId, userId).firstResult();
        if (member == null) {
            return NONE;
        }
        if (member.isAdmin()) {
            return moduleAuthorizationService.isModuleEnabled(workspaceId, moduleId) ? EDIT : NONE;
        }
        WorkspaceMemberModuleEntity row = WorkspaceMemberModuleEntity.find(
                "workspaceId = ?1 and userId = ?2 and moduleId = ?3", workspaceId, userId, moduleId)
                .firstResult();
        return row != null ? row.accessLevel : NONE;
    }

    public Optional<String> permissionDeniedReason(String workspaceId, String userId, String path, String httpMethod) {
        if (securityContext.isSuperAdmin()) {
            return Optional.empty();
        }
        Optional<PathRule> rule = WorkspaceModuleCatalog.matchRule(path);
        if (rule.isEmpty()) {
            return Optional.empty();
        }
        PathRule pathRule = rule.get();
        boolean write = isWriteMethod(httpMethod);
        if (pathRule.mode() == MatchMode.ANY) {
            boolean allowed = pathRule.moduleIds().stream()
                    .anyMatch(modId -> hasAccess(workspaceId, userId, modId, write));
            return allowed ? Optional.empty() : Optional.of("MODULE_FORBIDDEN");
        }
        for (String moduleId : pathRule.moduleIds()) {
            if (!hasAccess(workspaceId, userId, moduleId, write)) {
                return Optional.of("MODULE_FORBIDDEN");
            }
        }
        return Optional.empty();
    }

    private boolean hasAccess(String workspaceId, String userId, String moduleId, boolean write) {
        if (!moduleAuthorizationService.isModuleEnabled(workspaceId, moduleId)) {
            return false;
        }
        String level = accessLevel(workspaceId, userId, moduleId);
        if (NONE.equals(level)) {
            return false;
        }
        if (write) {
            return EDIT.equals(level);
        }
        return VIEW.equals(level) || EDIT.equals(level);
    }

    private static boolean isWriteMethod(String method) {
        if (method == null) {
            return false;
        }
        return Set.of("POST", "PUT", "PATCH", "DELETE").contains(method.toUpperCase());
    }
}
