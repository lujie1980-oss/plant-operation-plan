package com.plantops.api;

import com.plantops.api.dto.slitting.CreateSlittingPlanRequest;
import com.plantops.api.dto.slitting.OptimizeMasterRequest;
import com.plantops.api.dto.slitting.SaveSlittingAssignmentsRequest;
import com.plantops.api.dto.slitting.SaveSlittingTreeRequest;
import com.plantops.api.dto.slitting.SlittingPlanSummaryDto;
import com.plantops.api.dto.slitting.SlittingPlanTreeDto;
import com.plantops.scenario.slitting.SlittingPlanService;
import com.plantops.scenario.slitting.SlittingStudioOptimizeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Path("/api/v1/slitting/plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingPlanResource {

    @Inject
    SlittingPlanService slittingPlanService;

    @Inject
    SlittingStudioOptimizeService slittingStudioOptimizeService;

    @GET
    public List<SlittingPlanSummaryDto> list() {
        return slittingPlanService.listPlans();
    }

    @POST
    public SlittingPlanSummaryDto create(CreateSlittingPlanRequest request) {
        return slittingPlanService.createPlan(request);
    }

    @GET
    @Path("/{planVersionId}")
    public SlittingPlanSummaryDto get(@PathParam("planVersionId") String planVersionId) {
        return slittingPlanService.getPlan(planVersionId);
    }

    @GET
    @Path("/{planVersionId}/tree")
    public SlittingPlanTreeDto getTree(@PathParam("planVersionId") String planVersionId) {
        return slittingPlanService.getTree(planVersionId);
    }

    @POST
    @Path("/{planVersionId}/solve")
    public SlittingPlanSummaryDto solve(@PathParam("planVersionId") String planVersionId)
            throws ExecutionException, InterruptedException {
        return slittingPlanService.solvePlan(planVersionId);
    }

    @POST
    @Path("/{planVersionId}/masters/{masterNodeId}/optimize")
    public SlittingPlanTreeDto optimizeMaster(
            @PathParam("planVersionId") String planVersionId,
            @PathParam("masterNodeId") String masterNodeId,
            OptimizeMasterRequest request)
            throws ExecutionException, InterruptedException {
        return slittingStudioOptimizeService.optimizeMaster(
                planVersionId,
                masterNodeId,
                request != null ? request.orderCodes() : null);
    }

    @PUT
    @Path("/{planVersionId}/assignments")
    public SlittingPlanTreeDto saveAssignments(
            @PathParam("planVersionId") String planVersionId,
            SaveSlittingAssignmentsRequest request) {
        return slittingPlanService.saveAssignments(planVersionId, request);
    }

    @PUT
    @Path("/{planVersionId}/tree")
    public SlittingPlanTreeDto saveTree(
            @PathParam("planVersionId") String planVersionId,
            SaveSlittingTreeRequest request) {
        return slittingPlanService.saveTree(planVersionId, request);
    }
}
