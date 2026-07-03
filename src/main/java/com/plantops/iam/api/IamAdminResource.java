package com.plantops.iam.api;

import com.plantops.iam.dto.*;
import com.plantops.iam.service.IamAdminService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IamAdminResource {

    @Inject
    IamAdminService iamAdminService;

    @GET
    @Path("/users")
    public List<AdminUserDto> listUsers() {
        return iamAdminService.listUsers();
    }

    @POST
    @Path("/users")
    public AdminUserDto createUser(CreateAdminUserRequest request) {
        return iamAdminService.createUser(request);
    }

    @PATCH
    @Path("/users/{userId}")
    public AdminUserDto patchUser(@PathParam("userId") String userId, PatchAdminUserRequest request) {
        return iamAdminService.patchUser(userId, request);
    }

    @GET
    @Path("/workspaces")
    public List<AdminWorkspaceDto> listWorkspaces() {
        return iamAdminService.listWorkspaces();
    }
}
