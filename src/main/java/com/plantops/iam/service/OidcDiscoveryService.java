package com.plantops.iam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.iam.config.IamSecurityConfig;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Loads OIDC discovery document when {@code plantops.security.oidc.enabled=true}.
 */
@ApplicationScoped
public class OidcDiscoveryService {

    private static final Logger LOG = Logger.getLogger(OidcDiscoveryService.class);

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    ObjectMapper objectMapper;

    private volatile String issuer;
    private volatile String authorizationEndpoint;
    private volatile String tokenEndpoint;
    private volatile String jwksUri;

    void onStart(@Observes StartupEvent event) {
        ensureReady();
    }

    /**
     * Lazy-load discovery (retries when Keycloak starts after the app).
     */
    public synchronized void ensureReady() {
        if (ready() || !securityConfig.oidc().enabled()) {
            return;
        }
        String base = normalizeAuthServerUrl(securityConfig.oidc().authServerUrl());
        if (base.isBlank()) {
            LOG.warn("OIDC enabled but plantops.security.oidc.auth-server-url is blank");
            return;
        }
        try {
            loadDiscovery(base);
        } catch (Exception e) {
            LOG.warnf(e, "OIDC discovery not available yet: %s", base);
        }
    }

    public Optional<String> authorizationEndpoint() {
        ensureReady();
        return Optional.ofNullable(authorizationEndpoint);
    }

    public Optional<String> tokenEndpoint() {
        ensureReady();
        return Optional.ofNullable(tokenEndpoint);
    }

    public Optional<String> jwksUri() {
        ensureReady();
        return Optional.ofNullable(jwksUri);
    }

    public Optional<String> issuer() {
        ensureReady();
        return Optional.ofNullable(issuer);
    }

    public boolean ready() {
        return issuer != null && jwksUri != null && tokenEndpoint != null;
    }

    public String buildAuthorizationUrl(String redirectUri, String state) {
        String endpoint = authorizationEndpoint()
                .orElseThrow(() -> new IllegalStateException("OIDC discovery not loaded"));
        String clientId = securityConfig.oidc().clientId();
        String scope = URLEncoder.encode("openid profile email", StandardCharsets.UTF_8);
        return endpoint
                + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8);
    }

    private void loadDiscovery(String base) throws Exception {
        String discoveryUrl = base.endsWith("/")
                ? base + ".well-known/openid-configuration"
                : base + "/.well-known/openid-configuration";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(discoveryUrl))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("OIDC discovery HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        issuer = text(root, "issuer");
        authorizationEndpoint = text(root, "authorization_endpoint");
        tokenEndpoint = text(root, "token_endpoint");
        jwksUri = text(root, "jwks_uri");
        LOG.infof("OIDC discovery loaded: issuer=%s", issuer);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalStateException("OIDC discovery missing field: " + field);
        }
        return node.asText();
    }

    static String normalizeAuthServerUrl(String url) {
        return url == null ? "" : url.trim();
    }
}
