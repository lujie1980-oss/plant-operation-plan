package com.plantops.api;

import com.plantops.api.dto.MasterPlanObjectiveDto;
import com.plantops.api.dto.MasterPlanObjectivesUpdateRequest;
import com.plantops.config.MasterPlanObjectiveConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/planning/master-plan/objectives")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MasterPlanObjectiveResource {

    @Inject
    MasterPlanObjectiveConfigService objectiveConfigService;

    @GET
    public List<MasterPlanObjectiveDto> list() {
        return objectiveConfigService.listObjectives();
    }

    @PUT
    public List<MasterPlanObjectiveDto> update(MasterPlanObjectivesUpdateRequest request) {
        return objectiveConfigService.saveObjectives(request.objectives());
    }

    @POST
    @Path("/reset-defaults")
    public List<MasterPlanObjectiveDto> resetDefaults() {
        return objectiveConfigService.resetToDefaults();
    }
}
