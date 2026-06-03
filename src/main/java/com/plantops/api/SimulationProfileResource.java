package com.plantops.api;

import com.plantops.api.dto.planning.SaveSimulationProfileRequest;
import com.plantops.api.dto.planning.SimulationProfileDto;
import com.plantops.scenario.planning.SimulationProfileService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/planning/simulation-profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SimulationProfileResource {

    @Inject
    SimulationProfileService simulationProfileService;

    @GET
    public List<SimulationProfileDto> list() {
        return simulationProfileService.list();
    }

    @GET
    @Path("/{profileId}")
    public SimulationProfileDto get(@PathParam("profileId") String profileId) {
        return simulationProfileService.get(profileId);
    }

    @POST
    public Response save(SaveSimulationProfileRequest request) {
        SimulationProfileDto saved = simulationProfileService.save(request);
        return Response.ok(saved).build();
    }

    @DELETE
    @Path("/{profileId}")
    public Response delete(@PathParam("profileId") String profileId) {
        simulationProfileService.delete(profileId);
        return Response.noContent().build();
    }
}
