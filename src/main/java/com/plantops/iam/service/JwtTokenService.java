package com.plantops.iam.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.plantops.iam.config.IamSecurityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class JwtTokenService {

    @Inject
    IamSecurityConfig securityConfig;

    public String issue(String userId, String displayName, boolean superAdmin) {
        Algorithm algorithm = algorithm();
        Instant expires = Instant.now().plus(securityConfig.jwt().ttlHours(), ChronoUnit.HOURS);
        return JWT.create()
                .withIssuer(securityConfig.jwt().issuer())
                .withSubject(userId)
                .withClaim("displayName", displayName)
                .withClaim("isSuperAdmin", superAdmin)
                .withExpiresAt(expires)
                .sign(algorithm);
    }

    public DecodedJWT verify(String rawToken) throws JWTVerificationException {
        return JWT.require(algorithm())
                .withIssuer(securityConfig.jwt().issuer())
                .build()
                .verify(rawToken);
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(securityConfig.jwt().secret());
    }
}
