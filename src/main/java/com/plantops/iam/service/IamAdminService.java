package com.plantops.iam.service;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.dto.*;
import com.plantops.iam.entity.AppUserEntity;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class IamAdminService {

    private static final Pattern USER_ID = Pattern.compile("^[a-z][a-z0-9_-]{1,48}$");

    @Inject
    SecurityContext securityContext;

    @Inject
    PasswordService passwordService;

    @Inject
    IamAuditService auditService;

    public List<AdminUserDto> listUsers() {
        requireSuperAdmin();
        return AppUserEntity.<AppUserEntity>listAll().stream().map(this::toUserDto).toList();
    }

    @Transactional
    public AdminUserDto createUser(CreateAdminUserRequest request) {
        requireSuperAdmin();
        validateCreate(request);
        if (AppUserEntity.findById(request.userId()) != null) {
            throw new BadRequestException("userId exists");
        }
        if (AppUserEntity.find("loginName", request.loginName().trim()).firstResult() != null) {
            throw new BadRequestException("loginName exists");
        }
        AppUserEntity user = new AppUserEntity();
        user.userId = request.userId().trim();
        user.loginName = request.loginName().trim();
        user.displayName = request.displayName().trim();
        user.passwordHash = passwordService.hash(request.password());
        user.superAdmin = request.isSuperAdmin();
        user.status = "ACTIVE";
        user.createdAt = LocalDateTime.now();
        user.persist();
        auditService.log(securityContext.getCurrentUserId(), "CREATE_USER", "USER", user.userId, null);
        return toUserDto(user);
    }

    @Transactional
    public AdminUserDto patchUser(String userId, PatchAdminUserRequest request) {
        requireSuperAdmin();
        AppUserEntity user = findUser(userId);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.displayName = request.displayName().trim();
        }
        if (request.status() != null) {
            user.status = request.status().trim();
        }
        if (request.isSuperAdmin() != null) {
            if (!request.isSuperAdmin() && user.superAdmin) {
                long superCount = AppUserEntity.count("superAdmin = true and status = 'ACTIVE'");
                if (superCount <= 1) {
                    throw new BadRequestException("至少保留一名 Super Admin");
                }
            }
            user.superAdmin = request.isSuperAdmin();
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.passwordHash = passwordService.hash(request.password());
        }
        auditService.log(securityContext.getCurrentUserId(), "PATCH_USER", "USER", userId, null);
        return toUserDto(user);
    }

    public List<AdminWorkspaceDto> listWorkspaces() {
        requireSuperAdmin();
        return WorkspaceEntity.listAllOrdered().stream().map(ws -> {
            int members = (int) WorkspaceMemberEntity.count("workspaceId", ws.workspaceId);
            return new AdminWorkspaceDto(
                    ws.workspaceId,
                    ws.name,
                    ws.ownerUserId,
                    ws.workspaceType,
                    members,
                    ws.createdAt);
        }).toList();
    }

    private AdminUserDto toUserDto(AppUserEntity user) {
        return new AdminUserDto(
                user.userId,
                user.loginName,
                user.displayName,
                user.superAdmin,
                user.status,
                user.lastLoginAt,
                user.createdAt);
    }

    private AppUserEntity findUser(String userId) {
        AppUserEntity user = AppUserEntity.findById(userId);
        if (user == null) {
            throw new NotFoundException("user not found: " + userId);
        }
        return user;
    }

    private void validateCreate(CreateAdminUserRequest request) {
        if (request == null
                || request.userId() == null
                || request.loginName() == null
                || request.displayName() == null
                || request.password() == null) {
            throw new BadRequestException("userId, loginName, displayName, password required");
        }
        if (!USER_ID.matcher(request.userId().trim()).matches()) {
            throw new BadRequestException("invalid userId");
        }
    }

    private void requireSuperAdmin() {
        if (!securityContext.isSuperAdmin()) {
            throw new ForbiddenException("IAM_FORBIDDEN");
        }
    }
}
