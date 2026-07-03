package com.plantops.api;

import com.plantops.api.dto.slitting.SlittingSolverRunDto;
import com.plantops.scenario.slitting.SlittingSolverRunService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/slitting/solver-runs")
@Produces(MediaType.APPLICATION_JSON)
public class SlittingSolverRunResource {

    @Inject
    SlittingSolverRunService runService;

    @GET
    public List<SlittingSolverRunDto> list(@QueryParam("limit") Integer limit) {
        int n = limit != null && limit > 0 ? Math.min(limit, 100) : 30;
        return runService.listRecent(n);
    }

    @GET
    @Path("/{runId}")
    public SlittingSolverRunDto get(@PathParam("runId") String runId) {
        SlittingSolverRunDto dto = runService.getRun(runId);
        if (dto == null) {
            throw new NotFoundException("solver run not found: " + runId);
        }
        return dto;
    }
}
