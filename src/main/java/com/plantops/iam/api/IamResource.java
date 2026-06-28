package com.plantops.iam.api;

import com.plantops.iam.service.IamService;
import com.plantops.iam.dto.WorkspaceMembershipDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api/v1/iam")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IamResource {

    @Inject
    IamService iamService;

    @GET
    @Path("/me")
    public IamService.CurrentUser me() {
        return iamService.currentUser();
    }

    @GET
    @Path("/workspaces")
    public List<WorkspaceMembershipDto> myWorkspaces() {
        return iamService.workspaceMemberships();
    }
}
