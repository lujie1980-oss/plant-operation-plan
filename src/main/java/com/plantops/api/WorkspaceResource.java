package com.plantops.api;

import com.plantops.api.dto.WorkspaceCreateRequest;
import com.plantops.api.dto.WorkspaceDto;
import com.plantops.workspace.WorkspaceService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/workspaces")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkspaceResource {

    @Inject
    WorkspaceService workspaceService;

    @GET
    public List<WorkspaceDto> list() {
        return workspaceService.list();
    }

    @GET
    @Path("/{id}")
    public WorkspaceDto get(@PathParam("id") String id) {
        return workspaceService.get(id);
    }

    @POST
    public Response create(WorkspaceCreateRequest request) {
        WorkspaceDto created = workspaceService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        workspaceService.delete(id);
        return Response.noContent().build();
    }
}
