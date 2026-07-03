package com.plantops.iam.dto;

public record AuthConfigDto(
        boolean devMode,
        boolean registrationEnabled,
        boolean localLoginEnabled,
        OidcConfigDto oidc) {

    public record OidcConfigDto(
            boolean enabled,
            String authorizationEndpoint,
            String clientId) {}
}
