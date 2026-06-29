package com.plantops.iam.api;

import com.plantops.iam.dto.*;
import com.plantops.iam.service.AuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

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
}
