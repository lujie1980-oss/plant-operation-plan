package com.plantops.iam.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.plantops.iam.config.IamSecurityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.Optional;

@ApplicationScoped
public class OidcTokenService {

    private static final Logger LOG = Logger.getLogger(OidcTokenService.class);

    @Inject
    IamSecurityConfig securityConfig;

    @Inject
    OidcDiscoveryService discoveryService;

    public Optional<DecodedJWT> verify(String rawToken) {
        if (!securityConfig.oidc().enabled() || !discoveryService.ready()) {
            return Optional.empty();
        }
        try {
            DecodedJWT unsigned = JWT.decode(rawToken);
            String jwksUri = discoveryService.jwksUri().orElseThrow();
            JwkProvider provider = new UrlJwkProvider(new URL(jwksUri));
            Jwk jwk = provider.get(unsigned.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
            DecodedJWT verified = JWT.require(algorithm)
                    .withIssuer(discoveryService.issuer().orElseThrow())
                    .acceptLeeway(60)
                    .build()
                    .verify(rawToken);
            return Optional.of(verified);
        } catch (JWTVerificationException e) {
            LOG.debugf(e, "OIDC JWT verify failed");
            return Optional.empty();
        } catch (Exception e) {
            LOG.debugf(e, "OIDC JWT verify error");
            return Optional.empty();
        }
    }

    public Optional<String> username(DecodedJWT jwt) {
        String claim = securityConfig.oidc().usernameClaim();
        String value = jwt.getClaim(claim).asString();
        if (value != null && !value.isBlank()) {
            return Optional.of(value.trim());
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return Optional.of(sub.trim());
        }
        return Optional.empty();
    }
}
