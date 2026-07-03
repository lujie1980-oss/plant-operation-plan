package com.plantops.api;

import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.MaterialDemandDetailDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.api.dto.SrpCapacityGanttDto;
import com.plantops.scenario.CapacityService;
import com.plantops.scenario.OntologyMaterialPlanningService;
import com.plantops.scenario.StandardResourcePeriodGanttService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/ontology/capacity")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyCapacityResource {

    @Inject
    CapacityService capacityService;

    @Inject
    StandardResourcePeriodGanttService srpCapacityGanttService;

    @POST
    @Path("/analyze")
    public CapacityAnalysisDto analyze(@QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            return capacityService.analyzeForMasterPlan(masterPlanVersionId);
        }
        return capacityService.analyze();
    }

    @GET
    @Path("/srp-gantt")
    public SrpCapacityGanttDto srpGantt(@QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return srpCapacityGanttService.buildForMasterPlan(masterPlanVersionId);
    }
}
