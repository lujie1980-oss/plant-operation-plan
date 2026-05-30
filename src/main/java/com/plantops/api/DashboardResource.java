package com.plantops.api;

import com.plantops.api.dto.DashboardSummaryDto;
import com.plantops.scenario.DashboardService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @Inject
    DashboardService dashboardService;

    @GET
    @Path("/summary")
    public DashboardSummaryDto summary() {
        return dashboardService.getSummary();
    }
}
