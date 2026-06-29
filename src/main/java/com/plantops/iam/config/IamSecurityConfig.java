package com.plantops.iam.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "plantops.security")
public interface IamSecurityConfig {

    /** When true, unauthenticated requests are treated as dev super-user. */
    boolean devMode();

    boolean registrationEnabled();

    Jwt jwt();

    interface Jwt {
        String secret();

        String issuer();

        int ttlHours();
    }
}
