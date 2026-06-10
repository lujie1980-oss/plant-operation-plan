package com.plantops.api;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.OperationSnapshotDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.PispSummaryDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.SrpSnapshotDto;
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
import java.util.List;

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

    @GET
    @Path("/{sessionId}/pisps")
    public List<PispSummaryDto> listPisps(@PathParam("sessionId") String sessionId) {
        return sessionService.listPisps(sessionId);
    }

    @GET
    @Path("/{sessionId}/pisps/{pispId}/periods")
    public List<PispPeriodSnapshotDto> listPispPeriods(
            @PathParam("sessionId") String sessionId,
            @PathParam("pispId") String pispId) {
        return sessionService.listPispPeriods(sessionId, pispId);
    }

    @GET
    @Path("/{sessionId}/resources")
    public List<SrpSnapshotDto> listResources(@PathParam("sessionId") String sessionId) {
        return sessionService.listResources(sessionId);
    }

    @GET
    @Path("/{sessionId}/supply-orders/{supplyOrderId}/operations")
    public List<OperationSnapshotDto> listOperations(
            @PathParam("sessionId") String sessionId,
            @PathParam("supplyOrderId") String supplyOrderId) {
        return sessionService.listOperations(sessionId, supplyOrderId);
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
    @Path("/{sessionId}/optimize")
    public MasterPlanSessionOptimizeResultDto optimize(@PathParam("sessionId") String sessionId) throws Exception {
        return sessionService.optimize(sessionId);
    }

    @POST
    @Path("/{sessionId}/confirm")
    public MasterPlanSessionConfirmResultDto confirm(@PathParam("sessionId") String sessionId) throws Exception {
        return sessionService.confirm(sessionId);
    }
}
