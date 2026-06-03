package com.plantops.api;

import com.plantops.api.dto.execution.ConfirmScheduleSessionResultDto;
import com.plantops.api.dto.execution.CreateScheduleSessionRequest;
import com.plantops.api.dto.execution.ScheduleSessionDto;
import com.plantops.api.dto.execution.ScheduleSessionSimulateResultDto;
import com.plantops.api.dto.planning.SessionStepPatchDto;
import com.plantops.api.dto.planning.SimulateScheduleSessionRequest;
import com.plantops.scenario.DetailScheduleSessionService;
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

import java.util.List;

@Path("/api/v1/planning/schedule-sessions")
@Produces(MediaType.APPLICATION_JSON)
public class ScheduleSessionResource {

    @Inject
    DetailScheduleSessionService sessionService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateScheduleSessionRequest request) throws Exception {
        ScheduleSessionDto session = sessionService.create(request);
        return Response.status(Response.Status.CREATED).entity(session).build();
    }

    @GET
    @Path("/{sessionId}")
    public ScheduleSessionDto get(@PathParam("sessionId") String sessionId) {
        return sessionService.get(sessionId);
    }

    @GET
    @Path("/{sessionId}/operations/{operationId}/candidate-lines")
    public List<String> candidateLines(
            @PathParam("sessionId") String sessionId,
            @PathParam("operationId") String operationId) {
        return sessionService.candidateLines(sessionId, operationId);
    }

    @POST
    @Path("/{sessionId}/optimize")
    public ScheduleSessionDto optimize(@PathParam("sessionId") String sessionId) throws Exception {
        return sessionService.optimize(sessionId);
    }

    @POST
    @Path("/{sessionId}/simulate")
    @Consumes(MediaType.APPLICATION_JSON)
    public ScheduleSessionSimulateResultDto simulate(
            @PathParam("sessionId") String sessionId,
            SimulateScheduleSessionRequest request) {
        return sessionService.simulate(
                sessionId,
                request != null
                        ? request
                        : new SimulateScheduleSessionRequest(null, null, null, null, null, null));
    }

    @PATCH
    @Path("/{sessionId}/steps")
    @Consumes(MediaType.APPLICATION_JSON)
    public ScheduleSessionSimulateResultDto patchSteps(
            @PathParam("sessionId") String sessionId,
            List<SessionStepPatchDto> patches) {
        return sessionService.simulate(
                sessionId,
                new SimulateScheduleSessionRequest(patches, null, false, null, null, null));
    }

    @POST
    @Path("/{sessionId}/confirm")
    public ConfirmScheduleSessionResultDto confirm(@PathParam("sessionId") String sessionId) {
        return sessionService.confirm(sessionId);
    }
}
