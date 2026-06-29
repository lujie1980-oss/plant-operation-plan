package com.plantops.iam.filter;

import com.plantops.iam.config.IamSecurityConfig;
import com.plantops.iam.context.SecurityContext;
import com.plantops.iam.entity.AppUserEntity;
import com.plantops.iam.service.JwtTokenService;
import com.plantops.iam.service.OidcTokenService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * 认证过滤器 — priority=100。
 * dev-mode 且无 Bearer token 时注入 dev；否则解析本地 JWT 或 OIDC JWT。
 */
@Provider
@jakarta.annotation.Priority(100)
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthenticationFilter.class);

    @Inject
    SecurityContext securityContext;

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    OidcTokenService oidcTokenService;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String path = ctx.getUriInfo().getPath();
        if (isPublicPath(path)) {
            return;
        }

        String bearer = extractBearer(ctx);
        if (bearer == null) {
            if (securityConfig.devMode()) {
                securityContext.setDevUser();
                LOG.debug("IAM dev-mode: authenticated as dev (no token)");
                return;
            }
            abort(ctx, Response.Status.UNAUTHORIZED, "UNAUTHENTICATED");
            return;
        }

        if (authenticateLocalJwt(bearer)) {
            return;
        }
        if (authenticateOidcJwt(bearer)) {
            return;
        }
        abort(ctx, Response.Status.UNAUTHORIZED, "INVALID_TOKEN");
    }

    private boolean authenticateLocalJwt(String bearer) {
        try {
            DecodedJWT jwt = jwtTokenService.verify(bearer);
            return bindUser(jwt.getSubject());
        } catch (JWTVerificationException e) {
            LOG.debugf(e, "Local JWT verify failed");
            return false;
        }
    }

    private boolean authenticateOidcJwt(String bearer) {
        Optional<DecodedJWT> jwt = oidcTokenService.verify(bearer);
        if (jwt.isEmpty()) {
            return false;
        }
        Optional<String> loginName = oidcTokenService.username(jwt.get());
        if (loginName.isEmpty()) {
            return false;
        }
        AppUserEntity user = AppUserEntity.find("loginName", loginName.get()).firstResult();
        if (user == null || !"ACTIVE".equals(user.status)) {
            LOG.debugf("OIDC user not provisioned: %s", loginName.get());
            return false;
        }
        securityContext.setCurrentUserId(user.userId);
        securityContext.setDisplayName(user.displayName);
        securityContext.setSuperAdmin(user.superAdmin);
        securityContext.setDevMode(false);
        return true;
    }

    private boolean bindUser(String userId) {
        AppUserEntity user = AppUserEntity.findById(userId);
        if (user == null || !"ACTIVE".equals(user.status)) {
            return false;
        }
        securityContext.setCurrentUserId(user.userId);
        securityContext.setDisplayName(user.displayName);
        securityContext.setSuperAdmin(user.superAdmin);
        securityContext.setDevMode(false);
        return true;
    }

    static boolean isPublicPath(String path) {
        if (path == null) {
            return true;
        }
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.startsWith("api/v1/auth/")
                || p.equals("q/health")
                || p.startsWith("q/health/")
                || p.startsWith("q/openapi")
                || p.startsWith("q/swagger-ui");
    }

    private static String extractBearer(ContainerRequestContext ctx) {
        String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static void abort(ContainerRequestContext ctx, Response.Status status, String code) {
        ctx.abortWith(Response.status(status).entity("{\"error\":\"" + code + "\"}").build());
    }
}
