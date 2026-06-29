package com.plantops.iam.service;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.dto.*;
import com.plantops.iam.entity.AppUserEntity;
import com.plantops.iam.entity.WorkspaceEnabledAdapterEntity;
import com.plantops.iam.entity.WorkspaceEnabledModuleEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.iam.entity.WorkspaceMemberModuleEntity;
import com.plantops.iam.module.ModuleAuthorizationService;
import com.plantops.iam.module.WorkspaceModuleCatalog;
import com.plantops.persistence.entity.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class IamService {

    @Inject
    SecurityContext securityContext;

    @Inject
    ModuleAuthorizationService moduleAuthorizationService;

    @Inject
    MemberPermissionService memberPermissionService;

    public CurrentUser currentUser() {
        String userId = securityContext.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("Not authenticated");
        }
        List<WorkspaceMembershipDto> memberships = listMembershipsForUser(userId);
        boolean hasWorkspaces = !memberships.isEmpty();

        return new CurrentUser(userId, securityContext.getDisplayName(),
                securityContext.isSuperAdmin(), hasWorkspaces, memberships);
    }

    /** 仅返回 workspace_member 中明确加入的数据集（登录后不自动拥有种子 WS）。 */
    private List<WorkspaceMembershipDto> listMembershipsForUser(String userId) {
        @SuppressWarnings("unchecked")
        List<WorkspaceMemberEntity> members = WorkspaceMemberEntity.find("userId", userId).list();
        return members.stream()
                .map(member -> {
                    WorkspaceEntity ws = WorkspaceEntity.findByWorkspaceId(member.workspaceId);
                    if (ws == null) {
                        return null;
                    }
                    List<String> enabledModules = moduleAuthorizationService.enabledModuleMap(ws.workspaceId)
                            .entrySet().stream()
                            .filter(Map.Entry::getValue)
                            .map(Map.Entry::getKey)
                            .toList();
                    return new WorkspaceMembershipDto(ws.workspaceId, ws.name, member.role, enabledModules);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(WorkspaceMembershipDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<WorkspaceMembershipDto> workspaceMemberships() {
        return currentUser().workspaces();
    }

    public List<ModuleToggleDto> listWorkspaceModules(String workspaceId) {
        requireCanViewWorkspace(workspaceId);
        return WorkspaceModuleCatalog.MODULES.stream()
                .map(def -> new ModuleToggleDto(
                        def.id(),
                        def.name(),
                        def.categoryId(),
                        moduleAuthorizationService.isModuleEnabled(workspaceId, def.id())))
                .toList();
    }

    public List<AdapterToggleDto> listWorkspaceAdapters(String workspaceId) {
        requireCanViewWorkspace(workspaceId);
        return WorkspaceModuleCatalog.ADAPTERS.stream()
                .map(def -> new AdapterToggleDto(
                        def.id(),
                        def.name(),
                        def.type(),
                        moduleAuthorizationService.isAdapterEnabled(workspaceId, def.id())))
                .toList();
    }

    @Transactional
    public List<ModuleToggleDto> updateWorkspaceModules(String workspaceId, UpdateModulesRequest request) {
        requireCanManageWorkspace(workspaceId);
        if (request == null || request.modules() == null) {
            throw new IllegalArgumentException("modules required");
        }
        for (UpdateModulesRequest.ModuleToggleRequest item : request.modules()) {
            if (!WorkspaceModuleCatalog.KNOWN_MODULE_IDS.contains(item.moduleId())) {
                throw new IllegalArgumentException("unknown moduleId: " + item.moduleId());
            }
            upsertModule(workspaceId, item.moduleId(), item.enabled());
        }
        return listWorkspaceModules(workspaceId);
    }

    @Transactional
    public List<AdapterToggleDto> updateWorkspaceAdapters(String workspaceId, UpdateAdaptersRequest request) {
        requireCanManageWorkspace(workspaceId);
        if (request == null || request.adapters() == null) {
            throw new IllegalArgumentException("adapters required");
        }
        for (UpdateAdaptersRequest.AdapterToggleRequest item : request.adapters()) {
            if (!WorkspaceModuleCatalog.KNOWN_ADAPTER_IDS.contains(item.adapterId())) {
                throw new IllegalArgumentException("unknown adapterId: " + item.adapterId());
            }
            upsertAdapter(workspaceId, item.adapterId(), item.enabled());
        }
        return listWorkspaceAdapters(workspaceId);
    }

    public List<WorkspaceMemberDto> listWorkspaceMembers(String workspaceId) {
        requireCanViewWorkspace(workspaceId);
        return WorkspaceMemberEntity.<WorkspaceMemberEntity>list("workspaceId", workspaceId).stream()
                .map(this::toMemberDto)
                .toList();
    }

    @Transactional
    public WorkspaceMemberDto addWorkspaceMember(String workspaceId, AddWorkspaceMemberRequest request) {
        requireCanManageWorkspace(workspaceId);
        if (request == null || request.userId() == null || request.role() == null) {
            throw new BadRequestException("userId and role required");
        }
        String role = request.role().trim().toUpperCase();
        if (!List.of("MEMBER", "WS_ADMIN").contains(role)) {
            throw new BadRequestException("role must be MEMBER or WS_ADMIN");
        }
        AppUserEntity user = AppUserEntity.findById(request.userId().trim());
        if (user == null) {
            throw new NotFoundException("user not found: " + request.userId());
        }
        if (WorkspaceMemberEntity.count("workspaceId = ?1 and userId = ?2", workspaceId, user.userId) > 0) {
            throw new BadRequestException("user already member");
        }
        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.workspaceId = workspaceId;
        member.userId = user.userId;
        member.role = role;
        member.persist();
        return toMemberDto(member);
    }

    @Transactional
    public void removeWorkspaceMember(String workspaceId, String userId) {
        requireCanManageWorkspace(workspaceId);
        WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                "workspaceId = ?1 and userId = ?2", workspaceId, userId).firstResult();
        if (member == null) {
            throw new NotFoundException("member not found");
        }
        if (member.isOwner()) {
            throw new BadRequestException("cannot remove workspace owner");
        }
        WorkspaceMemberModuleEntity.delete("workspaceId = ?1 and userId = ?2", workspaceId, userId);
        member.delete();
    }

    public List<ModulePermissionDto> listMemberPermissions(String workspaceId, String userId) {
        requireCanManageWorkspace(workspaceId);
        ensureMember(workspaceId, userId);
        return WorkspaceModuleCatalog.MODULES.stream()
                .map(def -> new ModulePermissionDto(
                        def.id(),
                        def.name(),
                        memberPermissionService.accessLevel(workspaceId, userId, def.id())))
                .toList();
    }

    @Transactional
    public List<ModulePermissionDto> updateMemberPermissions(
            String workspaceId, String userId, UpdateMemberPermissionsRequest request) {
        requireCanManageWorkspace(workspaceId);
        WorkspaceMemberEntity member = ensureMember(workspaceId, userId);
        if (member.isAdmin()) {
            throw new BadRequestException("admin members have implicit EDIT; edit role instead");
        }
        if (request == null || request.permissions() == null) {
            throw new BadRequestException("permissions required");
        }
        for (UpdateMemberPermissionsRequest.PermissionEntry entry : request.permissions()) {
            if (!WorkspaceModuleCatalog.KNOWN_MODULE_IDS.contains(entry.moduleId())) {
                throw new BadRequestException("unknown moduleId: " + entry.moduleId());
            }
            String level = entry.accessLevel() != null ? entry.accessLevel().trim().toUpperCase() : MemberPermissionService.NONE;
            if (!List.of(MemberPermissionService.NONE, MemberPermissionService.VIEW, MemberPermissionService.EDIT).contains(level)) {
                throw new BadRequestException("invalid accessLevel: " + entry.accessLevel());
            }
            upsertMemberPermission(workspaceId, userId, entry.moduleId(), level);
        }
        return listMemberPermissions(workspaceId, userId);
    }

    private WorkspaceMemberEntity ensureMember(String workspaceId, String userId) {
        WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                "workspaceId = ?1 and userId = ?2", workspaceId, userId).firstResult();
        if (member == null) {
            throw new NotFoundException("member not found");
        }
        return member;
    }

    private WorkspaceMemberDto toMemberDto(WorkspaceMemberEntity member) {
        AppUserEntity user = AppUserEntity.findById(member.userId);
        return new WorkspaceMemberDto(
                member.userId,
                user != null ? user.displayName : member.userId,
                user != null ? user.loginName : member.userId,
                member.role);
    }

    private void upsertMemberPermission(String workspaceId, String userId, String moduleId, String level) {
        WorkspaceMemberModuleEntity row = WorkspaceMemberModuleEntity.find(
                "workspaceId = ?1 and userId = ?2 and moduleId = ?3", workspaceId, userId, moduleId)
                .firstResult();
        if (row == null) {
            row = new WorkspaceMemberModuleEntity();
            row.workspaceId = workspaceId;
            row.userId = userId;
            row.moduleId = moduleId;
            row.accessLevel = level;
            row.persist();
        } else {
            row.accessLevel = level;
        }
    }

    private void upsertModule(String workspaceId, String moduleId, boolean enabled) {
        WorkspaceEnabledModuleEntity row = WorkspaceEnabledModuleEntity.find(
                "workspaceId = ?1 and moduleId = ?2", workspaceId, moduleId).firstResult();
        if (row == null) {
            row = new WorkspaceEnabledModuleEntity();
            row.workspaceId = workspaceId;
            row.moduleId = moduleId;
            row.enabled = enabled;
            row.persist();
        } else {
            row.enabled = enabled;
        }
    }

    private void upsertAdapter(String workspaceId, String adapterId, boolean enabled) {
        WorkspaceEnabledAdapterEntity row = WorkspaceEnabledAdapterEntity.find(
                "workspaceId = ?1 and adapterId = ?2", workspaceId, adapterId).firstResult();
        if (row == null) {
            row = new WorkspaceEnabledAdapterEntity();
            row.workspaceId = workspaceId;
            row.adapterId = adapterId;
            row.enabled = enabled;
            row.persist();
        } else {
            row.enabled = enabled;
        }
    }

    private void requireCanViewWorkspace(String workspaceId) {
        ensureWorkspaceExists(workspaceId);
        if (securityContext.isSuperAdmin() || securityContext.isDevMode()) {
            return;
        }
        String userId = securityContext.getCurrentUserId();
        WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                "workspaceId = ?1 and userId = ?2", workspaceId, userId).firstResult();
        if (member == null) {
            throw new ForbiddenException("WORKSPACE_FORBIDDEN");
        }
    }

    private void requireCanManageWorkspace(String workspaceId) {
        ensureWorkspaceExists(workspaceId);
        if (securityContext.isSuperAdmin()) {
            return;
        }
        String userId = securityContext.getCurrentUserId();
        WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                "workspaceId = ?1 and userId = ?2", workspaceId, userId).firstResult();
        if (member == null) {
            throw new ForbiddenException("WORKSPACE_FORBIDDEN");
        }
        if (!member.isAdmin()) {
            throw new ForbiddenException("MODULE_FORBIDDEN");
        }
    }

    private void ensureWorkspaceExists(String workspaceId) {
        if (WorkspaceEntity.findByWorkspaceId(workspaceId) == null) {
            throw new NotFoundException("workspace 不存在: " + workspaceId);
        }
    }

    public record CurrentUser(
            String userId,
            String displayName,
            boolean isSuperAdmin,
            boolean hasWorkspaces,
            List<WorkspaceMembershipDto> workspaces) {
    }
}
