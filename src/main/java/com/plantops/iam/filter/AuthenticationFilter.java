package com.plantops.iam.filter;

import com.plantops.iam.context.SecurityContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;

/**
 * 认证过滤器 — priority=100，最先执行。
 * M1: dev-mode 注入固定 dev 用户。prod 解析 JWT（M3）。
 */
@Provider
@jakarta.annotation.Priority(100)
public class AuthenticationFilter implements ContainerRequestFilter {

    static final Logger LOG = Logger.getLogger(AuthenticationFilter.class);

    @Inject
    SecurityContext securityContext;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        // M1: dev-mode always
        securityContext.setDevUser();
        LOG.debugf("IAM dev-mode: authenticated as dev");
    }
}
