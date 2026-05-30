package com.plantops.api;

import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.scenario.MaterialRequirementService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/material-requirements")
@Produces(MediaType.APPLICATION_JSON)
public class MaterialRequirementResource {

    @Inject
    MaterialRequirementService materialRequirementService;

    @GET
    @Path("/balance")
    public MaterialRequirementReportDto balance(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return materialRequirementService.getBalance(masterPlanVersionId);
    }

    @POST
    @Path("/compute")
    public MaterialRequirementReportDto compute(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return materialRequirementService.buildReport(masterPlanVersionId);
    }

    @GET
    @Path("/materials/{productCode}/demand-usages")
    public MaterialDemandDetailDto demandUsages(
            @PathParam("productCode") String productCode,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return materialRequirementService.buildDemandDetailTree(productCode, masterPlanVersionId);
    }
}
