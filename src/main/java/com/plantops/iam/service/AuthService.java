package com.plantops.iam.service;

import com.plantops.iam.config.IamSecurityConfig;
import com.plantops.iam.dto.AuthConfigDto;
import com.plantops.iam.dto.AuthTokenDto;
import com.plantops.iam.dto.LoginRequest;
import com.plantops.iam.dto.RegisterRequest;
import com.plantops.iam.entity.AppUserEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@ApplicationScoped
public class AuthService {

    private static final Pattern USER_ID = Pattern.compile("^[a-z][a-z0-9_-]{1,48}$");

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    PasswordService passwordService;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    OidcDiscoveryService oidcDiscoveryService;

    public AuthConfigDto config() {
        AuthConfigDto.OidcConfigDto oidc = new AuthConfigDto.OidcConfigDto(
                securityConfig.oidc().enabled(),
                oidcDiscoveryService.authorizationEndpoint().orElse(null),
                blankToNull(securityConfig.oidc().clientId()));
        return new AuthConfigDto(
                securityConfig.devMode(),
                securityConfig.registrationEnabled(),
                securityConfig.localLoginEnabled(),
                oidc);
    }

    @Transactional
    public AuthTokenDto login(LoginRequest request) {
        if (!securityConfig.localLoginEnabled()) {
            throw new BadRequestException("LOCAL_LOGIN_DISABLED");
        }
        if (request == null || request.loginName() == null || request.password() == null) {
            throw new BadRequestException("loginName and password required");
        }
        AppUserEntity user = AppUserEntity.find("loginName", request.loginName().trim()).firstResult();
        if (user == null || !"ACTIVE".equals(user.status)) {
            throw new NotAuthorizedException("INVALID_CREDENTIALS");
        }
        if (!passwordService.matches(request.password(), user.passwordHash)) {
            throw new NotAuthorizedException("INVALID_CREDENTIALS");
        }
        user.lastLoginAt = LocalDateTime.now();
        return tokenFor(user);
    }

    @Transactional
    public AuthTokenDto register(RegisterRequest request) {
        if (!securityConfig.registrationEnabled()) {
            throw new BadRequestException("REGISTRATION_DISABLED");
        }
        validateRegister(request);
        if (AppUserEntity.findById(request.userId()) != null) {
            throw new BadRequestException("userId already exists");
        }
        if (AppUserEntity.find("loginName", request.loginName().trim()).firstResult() != null) {
            throw new BadRequestException("loginName already exists");
        }
        AppUserEntity user = new AppUserEntity();
        user.userId = request.userId().trim();
        user.loginName = request.loginName().trim();
        user.displayName = request.displayName().trim();
        user.passwordHash = passwordService.hash(request.password());
        user.superAdmin = false;
        user.status = "ACTIVE";
        user.createdAt = LocalDateTime.now();
        user.persist();
        return tokenFor(user);
    }

    private AuthTokenDto tokenFor(AppUserEntity user) {
        String token = jwtTokenService.issue(user.userId, user.displayName, user.superAdmin);
        return new AuthTokenDto(
                token,
                "Bearer",
                securityConfig.jwt().ttlHours(),
                user.userId,
                user.displayName,
                user.superAdmin);
    }

    private void validateRegister(RegisterRequest request) {
        if (request == null
                || request.userId() == null
                || request.loginName() == null
                || request.displayName() == null
                || request.password() == null) {
            throw new BadRequestException("userId, loginName, displayName, password required");
        }
        if (!USER_ID.matcher(request.userId().trim()).matches()) {
            throw new BadRequestException("userId must be lowercase alphanumeric with _-");
        }
        if (request.password().length() < 4) {
            throw new BadRequestException("password too short");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
