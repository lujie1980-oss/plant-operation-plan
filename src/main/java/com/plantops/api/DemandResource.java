package com.plantops.api;

import com.plantops.api.dto.DemandPoolEntryDto;
import com.plantops.api.dto.DemandPoolSummaryDto;
import com.plantops.api.dto.DemandTrackingEntryDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderGenerateRequestDto;
import com.plantops.api.dto.WorkOrderGenerationBatchResultDto;
import com.plantops.api.dto.WorkOrderGenerationResultDto;
import com.plantops.api.dto.demand.OrderDemandActionRequest;
import com.plantops.api.dto.demand.OrderDemandActionResult;
import com.plantops.scenario.DemandService;
import com.plantops.scenario.OrderDemandAction;
import com.plantops.scenario.OrderDemandActionService;
import com.plantops.scenario.WorkOrderGenerationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/v1/demand")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DemandResource {

    @Inject
    DemandService demandService;

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    OrderDemandActionService orderDemandActionService;

    @GET
    @Path("/demand-pool")
    public List<DemandPoolEntryDto> demandPool(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return demandService.getDemandPool(masterPlanVersionId);
    }

    @GET
    @Path("/demand-pool/summary")
    public DemandPoolSummaryDto demandPoolSummary(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return demandService.getDemandPoolSummary(masterPlanVersionId);
    }

    @GET
    @Path("/tracking")
    public List<DemandTrackingEntryDto> demandTracking() {
        return demandService.getDemandTracking();
    }

    @GET
    @Path("/demand-pool/{salesOrderNo}/{salesOrderLineNo}/fulfillment-chain")
    public OrderFulfillmentChainDto fulfillmentChain(
            @PathParam("salesOrderNo") String salesOrderNo,
            @PathParam("salesOrderLineNo") int salesOrderLineNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return demandService.getFulfillmentChain(salesOrderNo, salesOrderLineNo, masterPlanVersionId);
    }

    @POST
    @Path("/demand-pool/{salesOrderNo}/{salesOrderLineNo}/actions/{action}")
    public OrderDemandActionResult orderAction(
            @PathParam("salesOrderNo") String salesOrderNo,
            @PathParam("salesOrderLineNo") int salesOrderLineNo,
            @PathParam("action") String action,
            OrderDemandActionRequest body) {
        return orderDemandActionService.execute(
                salesOrderNo,
                salesOrderLineNo,
                OrderDemandAction.parse(action),
                body);
    }

    @POST
    @Path("/import")
    public Response importDemand(List<DemandPoolEntryDto> entries) {
        int count = demandService.importOrders(entries);
        return Response.ok(Map.of("imported", count)).build();
    }

    /**
     * 按 BOM + productResources 生成工单树。
     * 无 body 或 allOpenOrders=true：处理全部开放订单行；否则指定 salesOrderNo + salesOrderLineNo。
     */
    @POST
    @Path("/work-orders/generate")
    public Response generateWorkOrders(WorkOrderGenerateRequestDto request) {
        boolean replace = request == null || request.replaceExisting() == null || request.replaceExisting();
        if (request == null || Boolean.TRUE.equals(request.allOpenOrders())
                || request.salesOrderNo() == null || request.salesOrderNo().isBlank()) {
            WorkOrderGenerationBatchResultDto batch =
                    workOrderGenerationService.generateForAllOpenOrders(replace);
            return Response.ok(batch).build();
        }
        if (request.salesOrderLineNo() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "salesOrderLineNo is required when salesOrderNo is set"))
                    .build();
        }
        WorkOrderGenerationResultDto one = workOrderGenerationService.generateForOrderLine(
                request.salesOrderNo(),
                request.salesOrderLineNo(),
                replace);
        return Response.ok(one).build();
    }

    @POST
    @Path("/work-orders/generate/{salesOrderNo}/{salesOrderLineNo}")
    public WorkOrderGenerationResultDto generateWorkOrdersForLine(
            @PathParam("salesOrderNo") String salesOrderNo,
            @PathParam("salesOrderLineNo") int salesOrderLineNo,
            @QueryParam("replaceExisting") @DefaultValue("true") boolean replaceExisting) {
        return workOrderGenerationService.generateForOrderLine(
                salesOrderNo, salesOrderLineNo, replaceExisting);
    }
}
