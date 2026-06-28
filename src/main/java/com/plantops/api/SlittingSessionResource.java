package com.plantops.api;

import com.plantops.api.dto.slitting.CreateSlittingSessionRequest;
import com.plantops.api.dto.slitting.PatchSlittingSessionRequest;
import com.plantops.api.dto.slitting.SlittingPlanTreeDto;
import com.plantops.api.dto.slitting.SlittingSessionDto;
import com.plantops.scenario.slitting.SlittingSessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.ExecutionException;

@Path("/api/v1/slitting/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingSessionResource {

    @Inject
    SlittingSessionService sessionService;

    @POST
    public Response create(CreateSlittingSessionRequest request) {
        SlittingSessionDto session = sessionService.create(request);
        return Response.status(Response.Status.CREATED).entity(session).build();
    }

    @GET
    @Path("/{sessionId}")
    public SlittingSessionDto get(@PathParam("sessionId") String sessionId) {
        return sessionService.get(sessionId);
    }

    @PATCH
    @Path("/{sessionId}")
    public SlittingSessionDto patch(
            @PathParam("sessionId") String sessionId,
            PatchSlittingSessionRequest request) {
        return sessionService.patch(sessionId, request);
    }

    @POST
    @Path("/{sessionId}/local-optimize")
    public SlittingSessionDto localOptimize(@PathParam("sessionId") String sessionId)
            throws ExecutionException, InterruptedException {
        return sessionService.localOptimize(sessionId);
    }

    @POST
    @Path("/{sessionId}/auto-nest")
    public SlittingSessionDto autoNest(@PathParam("sessionId") String sessionId) {
        return sessionService.autoNest(sessionId);
    }

    @POST
    @Path("/{sessionId}/confirm")
    public SlittingPlanTreeDto confirm(@PathParam("sessionId") String sessionId) {
        return sessionService.confirm(sessionId);
    }
}
