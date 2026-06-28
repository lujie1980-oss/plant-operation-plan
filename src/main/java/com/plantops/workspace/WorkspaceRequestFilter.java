package com.plantops.workspace;

import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.entity.WorkspaceMemberEntity;
import com.plantops.persistence.entity.WorkspaceEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class WorkspaceRequestFilter implements ContainerRequestFilter {

    @Inject
    WorkspaceContext workspaceContext;

    @Inject
    WorkspaceRegistry workspaceRegistry;

    @Inject
    SecurityContext securityContext;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        // 跳过平台级 API
        if (path != null && (path.startsWith("api/v1/workspaces")
                || path.startsWith("api/v1/iam/")
                || path.startsWith("api/v1/auth/")
                || path.startsWith("api/v1/admin/"))) {
            return;
        }

        String header = requestContext.getHeaderString(WorkspaceConstants.HEADER);
        if (header == null || header.isBlank()) {
            // 无 X-Workspace-Id：放行（用户在创建页或 dev-mode，由前端 hasWorkspaces 控制）
            return;
        }

        String id = header.trim();

        // 校验 workspace 存在
        if (!workspaceRegistry.exists(id)) {
            if (WorkspaceEntity.existsById(id)) {
                workspaceRegistry.register(id);
            } else {
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Unknown workspace: " + id + "\"}")
                        .build());
                return;
            }
        }

        // M1: 校验 workspace member（dev-mode 跳过）
        String userId = securityContext.getCurrentUserId();
        if (userId != null && !securityContext.isDevMode()) {
            WorkspaceMemberEntity member = WorkspaceMemberEntity.find(
                    "workspaceId = ?1 and userId = ?2", id, userId).firstResult();
            if (member == null) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity("{\"error\":\"WORKSPACE_FORBIDDEN\"}")
                        .build());
                return;
            }
            securityContext.setWorkspaceRoleFrom(member);
        }

        workspaceContext.setWorkspaceId(id);
    }
}
