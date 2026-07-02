package com.plantops.api;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanRequest;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.CreateSupplyPlanResultDto;
import com.plantops.api.dto.materialplanning.MaterialSupplyPlanningDtos.SupplyRoutingCandidateDto;
import com.plantops.scenario.OntologyMaterialPlanningService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/ontology/material-planning")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyMaterialPlanningResource {

    @Inject
    OntologyMaterialPlanningService ontologyMaterialPlanningService;

    @GET
    @Path("/balance")
    public MaterialRequirementReportDto balance(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyMaterialPlanningService.balance(masterPlanVersionId);
    }

    @POST
    @Path("/compute")
    public MaterialRequirementReportDto compute(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyMaterialPlanningService.compute(masterPlanVersionId);
    }

    @GET
    @Path("/materials/{productCode}/demand-detail")
    public MaterialDemandDetailDto demandDetail(
            @PathParam("productCode") String productCode,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyMaterialPlanningService.demandDetail(productCode, masterPlanVersionId);
    }

    @GET
    @Path("/pisps/{pispId}/routing-candidates")
    public List<SupplyRoutingCandidateDto> routingCandidates(
            @PathParam("pispId") String pispId,
            @QueryParam("periodFrom") String periodFrom,
            @QueryParam("periodTo") String periodTo,
            @QueryParam("quantity") Double quantity,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyMaterialPlanningService.routingCandidates(
                pispId, periodFrom, periodTo, quantity, masterPlanVersionId);
    }

    @POST
    @Path("/pisps/{pispId}/supply-plans")
    @Consumes(MediaType.APPLICATION_JSON)
    public CreateSupplyPlanResultDto createSupplyPlan(
            @PathParam("pispId") String pispId,
            CreateSupplyPlanRequest request,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyMaterialPlanningService.createSupplyPlan(pispId, request, masterPlanVersionId);
    }
}
