package com.plantops.iam.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationFilterTest {

    @Test
    void isPublicPath_includesAuthAndOidcExchange() {
        assertTrue(AuthenticationFilter.isPublicPath("api/v1/auth/config"));
        assertTrue(AuthenticationFilter.isPublicPath("api/v1/auth/login"));
        assertTrue(AuthenticationFilter.isPublicPath("api/v1/auth/oidc/exchange"));
        assertFalse(AuthenticationFilter.isPublicPath("api/v1/iam/me"));
    }
}
