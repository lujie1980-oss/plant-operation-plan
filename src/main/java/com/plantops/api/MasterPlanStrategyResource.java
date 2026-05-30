package com.plantops.api;

import com.plantops.api.dto.MasterPlanStrategyCreateRequest;
import com.plantops.api.dto.MasterPlanStrategyDetailDto;
import com.plantops.api.dto.MasterPlanStrategyDuplicateRequest;
import com.plantops.api.dto.MasterPlanStrategySummaryDto;
import com.plantops.api.dto.MasterPlanStrategyUpdateRequest;
import com.plantops.config.MasterPlanStrategyConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1/planning/master-plan/strategies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MasterPlanStrategyResource {

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @GET
    public List<MasterPlanStrategySummaryDto> list() {
        return strategyConfigService.listSummaries();
    }

    /** 当前工作区默认策略；路径勿用 {@code /default}，以免与策略 id {@code default} 冲突。 */
    @GET
    @Path("/by-default")
    public MasterPlanStrategyDetailDto getDefault() {
        return strategyConfigService.getDefaultDetail();
    }

    @GET
    @Path("/{strategyId}")
    public MasterPlanStrategyDetailDto get(@PathParam("strategyId") String strategyId) {
        return strategyConfigService.getDetail(strategyId);
    }

    @POST
    public Response create(MasterPlanStrategyCreateRequest request) {
        MasterPlanStrategyDetailDto created = strategyConfigService.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{strategyId}")
    public MasterPlanStrategyDetailDto update(
            @PathParam("strategyId") String strategyId,
            MasterPlanStrategyUpdateRequest request) {
        return strategyConfigService.update(strategyId, request);
    }

    @POST
    @Path("/{strategyId}/duplicate")
    public Response duplicate(
            @PathParam("strategyId") String strategyId,
            MasterPlanStrategyDuplicateRequest request) {
        MasterPlanStrategyDetailDto copy = strategyConfigService.duplicate(
                strategyId,
                request != null ? request.name() : null);
        return Response.status(Response.Status.CREATED).entity(copy).build();
    }

    @DELETE
    @Path("/{strategyId}")
    public Response delete(@PathParam("strategyId") String strategyId) {
        strategyConfigService.delete(strategyId);
        return Response.noContent().build();
    }
}
