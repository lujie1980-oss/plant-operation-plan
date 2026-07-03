package com.plantops.iam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.iam.config.IamSecurityConfig;
import com.plantops.iam.dto.AuthTokenDto;
import com.plantops.iam.entity.AppUserEntity;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class OidcAuthService {

    private static final Logger LOG = Logger.getLogger(OidcAuthService.class);

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    OidcDiscoveryService discoveryService;

    @Inject
    OidcTokenService oidcTokenService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public AuthTokenDto exchangeCode(String code, String redirectUri) {
        if (!securityConfig.oidc().enabled()) {
            throw new BadRequestException("OIDC_DISABLED");
        }
        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new BadRequestException("code and redirectUri required");
        }
        String accessToken = requestAccessToken(code.trim(), redirectUri.trim());
        DecodedJWT jwt = oidcTokenService.verify(accessToken)
                .orElseThrow(() -> new NotAuthorizedException("INVALID_OIDC_TOKEN"));
        String loginName = oidcTokenService.username(jwt)
                .orElseThrow(() -> new NotAuthorizedException("OIDC_USERNAME_MISSING"));
        AppUserEntity user = AppUserEntity.find("loginName", loginName).firstResult();
        if (user == null || !"ACTIVE".equals(user.status)) {
            throw new NotAuthorizedException("OIDC_USER_NOT_PROVISIONED");
        }
        user.lastLoginAt = LocalDateTime.now();
        return new AuthTokenDto(
                accessToken,
                "Bearer",
                securityConfig.jwt().ttlHours(),
                user.userId,
                user.displayName,
                user.superAdmin);
    }

    private String requestAccessToken(String code, String redirectUri) {
        String tokenEndpoint = discoveryService.tokenEndpoint()
                .orElseThrow(() -> new BadRequestException("OIDC_DISCOVERY_NOT_READY"));
        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&client_id=" + URLEncoder.encode(securityConfig.oidc().clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(securityConfig.oidc().clientSecret(), StandardCharsets.UTF_8);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(20))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warnf("OIDC token exchange failed: HTTP %d %s", response.statusCode(), response.body());
                throw new NotAuthorizedException("OIDC_TOKEN_EXCHANGE_FAILED");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode token = root.get("access_token");
            if (token == null || token.asText().isBlank()) {
                throw new NotAuthorizedException("OIDC_TOKEN_EXCHANGE_FAILED");
            }
            return token.asText();
        } catch (NotAuthorizedException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("OIDC token exchange error", e);
            throw new NotAuthorizedException("OIDC_TOKEN_EXCHANGE_FAILED");
        }
    }

    public Optional<String> authorizationUrl(String redirectUri) {
        if (!securityConfig.oidc().enabled() || !discoveryService.ready()) {
            return Optional.empty();
        }
        return Optional.of(discoveryService.buildAuthorizationUrl(redirectUri, "plantops"));
    }
}
