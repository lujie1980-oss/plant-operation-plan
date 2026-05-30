package com.plantops.workspace;

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

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (path != null && path.startsWith("api/v1/workspaces")) {
            return;
        }

        String header = requestContext.getHeaderString(WorkspaceConstants.HEADER);
        if (header == null || header.isBlank()) {
            workspaceContext.setWorkspaceId(WorkspaceConstants.DEFAULT_ID);
            return;
        }

        String id = header.trim();
        if (!workspaceRegistry.exists(id)) {
            // 兜底：若 registry 未刷新（例如运行中创建了新 workspace），允许一次 DB 校验并回填缓存
            if (WorkspaceEntity.existsById(id)) {
                workspaceRegistry.register(id);
            } else {
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Unknown workspace: " + id + "\"}")
                        .build());
                return;
            }
        }
        if (!workspaceRegistry.exists(id)) {
            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Unknown workspace: " + id + "\"}")
                    .build());
            return;
        }
        workspaceContext.setWorkspaceId(id);
    }
}
