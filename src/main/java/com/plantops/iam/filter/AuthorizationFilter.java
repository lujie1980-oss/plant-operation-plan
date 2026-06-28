package com.plantops.iam.filter;

import com.plantops.iam.context.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * 授权过滤器 — priority=300。
 * M1 空壳（仅记录 SecurityContext 就绪）。M2 起校验模块开关。M3 起校验 VIEW/EDIT。
 */
@Provider
@jakarta.annotation.Priority(300)
public class AuthorizationFilter implements ContainerRequestFilter {

    @Inject
    SecurityContext securityContext;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        // M1: no-op — all requests pass
    }
}
