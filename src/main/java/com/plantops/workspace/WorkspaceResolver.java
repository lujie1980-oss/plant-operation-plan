package com.plantops.workspace;

import io.quarkus.arc.Arc;

/**
 * 在非 HTTP 请求（如启动灌数）或无 Request 时回退到 default。
 */
public final class WorkspaceResolver {

    private WorkspaceResolver() {
    }

    public static String currentWorkspaceId() {
        if (Arc.container().requestContext().isActive()) {
            WorkspaceContext ctx = Arc.container().instance(WorkspaceContext.class).get();
            if (ctx != null && ctx.getWorkspaceId() != null && !ctx.getWorkspaceId().isBlank()) {
                return ctx.getWorkspaceId();
            }
        }
        return WorkspaceConstants.DEFAULT_ID;
    }
}
