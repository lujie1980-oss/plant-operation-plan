package com.plantops.api;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.scenario.OntologyMaterialPlanningService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
}
