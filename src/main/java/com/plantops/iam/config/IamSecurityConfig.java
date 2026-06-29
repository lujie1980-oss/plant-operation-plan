package com.plantops.iam.config;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Unremovable
public class IamSecurityConfig {

    @ConfigProperty(name = "plantops.security.dev-mode", defaultValue = "true")
    boolean devMode;

    @ConfigProperty(name = "plantops.security.registration-enabled", defaultValue = "false")
    boolean registrationEnabled;

    @ConfigProperty(name = "plantops.security.local-login-enabled", defaultValue = "true")
    boolean localLoginEnabled;

    @ConfigProperty(name = "plantops.security.jwt.secret", defaultValue = "plantops-dev-jwt-secret-key-min-32-chars!!")
    String jwtSecret;

    @ConfigProperty(name = "plantops.security.jwt.issuer", defaultValue = "plantops")
    String jwtIssuer;

    @ConfigProperty(name = "plantops.security.jwt.ttl-hours", defaultValue = "24")
    int jwtTtlHours;

    @ConfigProperty(name = "plantops.security.oidc.enabled", defaultValue = "false")
    boolean oidcEnabled;

    @ConfigProperty(name = "plantops.security.oidc.auth-server-url", defaultValue = "")
    String oidcAuthServerUrl;

    @ConfigProperty(name = "plantops.security.oidc.client-id", defaultValue = "")
    String oidcClientId;

    @ConfigProperty(name = "plantops.security.oidc.client-secret", defaultValue = "")
    String oidcClientSecret;

    @ConfigProperty(name = "plantops.security.oidc.username-claim", defaultValue = "preferred_username")
    String oidcUsernameClaim;

    public boolean devMode() {
        return devMode;
    }

    public boolean registrationEnabled() {
        return registrationEnabled;
    }

    public boolean localLoginEnabled() {
        return localLoginEnabled;
    }

    public Jwt jwt() {
        return new Jwt(jwtSecret, jwtIssuer, jwtTtlHours);
    }

    public Oidc oidc() {
        return new Oidc(oidcEnabled, oidcAuthServerUrl, oidcClientId, oidcClientSecret, oidcUsernameClaim);
    }

    public record Jwt(String secret, String issuer, int ttlHours) {}

    public record Oidc(
            boolean enabled,
            String authServerUrl,
            String clientId,
            String clientSecret,
            String usernameClaim) {}
}
