package com.plantops.iam.filter;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.module.ModuleAuthorizationService;
import com.plantops.iam.service.MemberPermissionService;
import com.plantops.workspace.WorkspaceContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Optional;

/**
 * 授权过滤器 — priority=300。
 * M2: 模块开关；M3: 成员 VIEW/EDIT（dev-mode / super-admin 跳过成员矩阵）。
 */
@Provider
@jakarta.annotation.Priority(300)
public class AuthorizationFilter implements ContainerRequestFilter {

    @Inject
    SecurityContext securityContext;

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    ModuleAuthorizationService moduleAuthorizationService;

    @Inject
    MemberPermissionService memberPermissionService;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (!securityContext.isAuthenticated()) {
            return;
        }
        String path = ctx.getUriInfo().getPath();
        String workspaceId = workspaceContext.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        Optional<String> moduleReason = moduleAuthorizationService.moduleDisabledReason(workspaceId, path);
        if (moduleReason.isPresent()) {
            abort(ctx, moduleReason.get());
            return;
        }
        Optional<String> adapterReason = moduleAuthorizationService.adapterDisabledReason(workspaceId, path);
        if (adapterReason.isPresent()) {
            abort(ctx, adapterReason.get());
            return;
        }

        String userId = securityContext.getCurrentUserId();
        if (userId == null) {
            return;
        }
        Optional<String> permReason = memberPermissionService.permissionDeniedReason(
                workspaceId, userId, path, ctx.getMethod());
        permReason.ifPresent(code -> abort(ctx, code));
    }

    private static void abort(ContainerRequestContext ctx, String code) {
        ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                .entity("{\"error\":\"" + code + "\"}")
                .build());
    }
}
