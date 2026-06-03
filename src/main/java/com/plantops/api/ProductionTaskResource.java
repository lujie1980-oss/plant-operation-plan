package com.plantops.api;

import com.plantops.api.dto.execution.ProductionTaskDto;
import com.plantops.scenario.execution.ProductionTaskService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/production-tasks")
@Produces(MediaType.APPLICATION_JSON)
public class ProductionTaskResource {

    @Inject
    ProductionTaskService productionTaskService;

    @GET
    public List<ProductionTaskDto> list(@QueryParam("executionState") String executionState) {
        if (executionState != null && !executionState.isBlank()) {
            return productionTaskService.listByState(executionState);
        }
        return productionTaskService.listAll();
    }

    @GET
    @Path("/{stepId}")
    public ProductionTaskDto get(@PathParam("stepId") String stepId) {
        return productionTaskService.get(stepId);
    }

    @POST
    @Path("/{stepId}/start")
    public ProductionTaskDto start(@PathParam("stepId") String stepId) {
        return productionTaskService.start(stepId);
    }

    @POST
    @Path("/{stepId}/complete")
    public ProductionTaskDto complete(@PathParam("stepId") String stepId) {
        return productionTaskService.complete(stepId);
    }
}
