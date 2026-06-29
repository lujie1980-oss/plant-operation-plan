package com.plantops.iam.api;

import com.plantops.iam.dto.*;
import com.plantops.iam.service.AuthService;
import com.plantops.iam.service.OidcAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    OidcAuthService oidcAuthService;

    @GET
    @Path("/config")
    public AuthConfigDto config() {
        return authService.config();
    }

    @POST
    @Path("/login")
    public AuthTokenDto login(LoginRequest request) {
        return authService.login(request);
    }

    @POST
    @Path("/register")
    public AuthTokenDto register(RegisterRequest request) {
        return authService.register(request);
    }

    @POST
    @Path("/oidc/exchange")
    public AuthTokenDto exchangeOidc(OidcExchangeRequest request) {
        return oidcAuthService.exchangeCode(request.code(), request.redirectUri());
    }

    @GET
    @Path("/oidc/authorize")
    public Response authorize(@QueryParam("redirect_uri") String redirectUri) {
        String url = oidcAuthService.authorizationUrl(redirectUri)
                .orElseThrow(() -> new BadRequestException("OIDC_NOT_READY"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authorizationUrl", url);
        return Response.ok(body).build();
    }
}
