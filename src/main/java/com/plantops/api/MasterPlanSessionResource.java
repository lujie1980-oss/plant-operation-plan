package com.plantops.api;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.scenario.planning.MasterPlanOntologySessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/master-plan/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class MasterPlanSessionResource {

    @Inject
    MasterPlanOntologySessionService sessionService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateMasterPlanSessionRequest request) {
        MasterPlanSessionDto session = sessionService.create(request);
        return Response.status(Response.Status.CREATED).entity(session).build();
    }

    @GET
    @Path("/{sessionId}")
    public MasterPlanSessionDto get(@PathParam("sessionId") String sessionId) {
        return sessionService.get(sessionId);
    }

    @POST
    @Path("/{sessionId}/simulate")
    @Consumes(MediaType.APPLICATION_JSON)
    public MasterPlanSessionSimulateResultDto simulate(
            @PathParam("sessionId") String sessionId,
            SimulateMasterPlanSessionRequest request) {
        return sessionService.simulate(sessionId, request);
    }

    @POST
    @Path("/{sessionId}/confirm")
    public Response confirm(@PathParam("sessionId") String sessionId) {
        sessionService.confirm(sessionId);
        return Response.noContent().build();
    }
}
