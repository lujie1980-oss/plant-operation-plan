package com.plantops.api;

import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.WorkOrderDispatchRequestDto;
import com.plantops.api.dto.WorkOrderDispatchResultDto;
import com.plantops.api.dto.WorkOrderDto;
import com.plantops.api.dto.WorkOrderGenerationBatchResultDto;
import com.plantops.api.dto.WorkOrderKittingDto;
import com.plantops.api.dto.WorkOrderPeggingDto;
import com.plantops.api.dto.WorkOrderScheduleOperationDto;
import com.plantops.api.dto.WorkOrderOrderLineTreeDto;
import com.plantops.scenario.WorkOrderGenerationService;
import com.plantops.scenario.WorkOrderOrderLineTreeService;
import com.plantops.scenario.WorkOrderService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/work-orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkOrderResource {

    @Inject
    WorkOrderService workOrderService;

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    WorkOrderOrderLineTreeService workOrderOrderLineTreeService;

    @GET
    public List<WorkOrderDto> list(@QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return workOrderService.listAll(masterPlanVersionId);
    }

    @GET
    @Path("/by-order-line/{salesOrderNo}/{salesOrderLineNo}")
    public WorkOrderOrderLineTreeDto listByOrderLine(
            @PathParam("salesOrderNo") String salesOrderNo,
            @PathParam("salesOrderLineNo") int salesOrderLineNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return workOrderOrderLineTreeService.buildTree(salesOrderNo, salesOrderLineNo, masterPlanVersionId);
    }

    @POST
    @Path("/generate-all")
    public WorkOrderGenerationBatchResultDto generateAll(
            @QueryParam("replaceExisting") @DefaultValue("true") boolean replaceExisting) {
        return workOrderGenerationService.generateForAllOpenOrders(replaceExisting);
    }

    @POST
    @Path("/dispatch")
    public WorkOrderDispatchResultDto dispatch(WorkOrderDispatchRequestDto request) {
        return workOrderService.dispatchForScheduling(request);
    }

    @GET
    @Path("/dispatched/kitting")
    public List<WorkOrderKittingDto> dispatchedKitting() {
        return workOrderService.kittingForDispatched();
    }

    @POST
    @Path("/dispatched/kitting/compute")
    public List<WorkOrderKittingDto> computeDispatchedKitting() {
        return workOrderService.recomputeDispatchedKitting();
    }

    @GET
    @Path("/{workOrderNo}/pegging")
    public List<WorkOrderPeggingDto> pegging(@PathParam("workOrderNo") String workOrderNo) {
        return workOrderService.listPegging(workOrderNo);
    }

    @GET
    @Path("/{workOrderNo}/fulfillment-chain")
    public OrderFulfillmentChainDto fulfillmentChain(
            @PathParam("workOrderNo") String workOrderNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return workOrderService.fulfillmentChain(workOrderNo, masterPlanVersionId);
    }

    @GET
    @Path("/{workOrderNo}/downstream-fulfillment-chain")
    public OrderFulfillmentChainDto downstreamFulfillmentChain(
            @PathParam("workOrderNo") String workOrderNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return workOrderService.downstreamFulfillmentChain(workOrderNo, masterPlanVersionId);
    }

    @GET
    @Path("/{workOrderNo}/schedule-operations")
    public List<WorkOrderScheduleOperationDto> scheduleOperations(
            @PathParam("workOrderNo") String workOrderNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return workOrderService.scheduleOperations(workOrderNo, masterPlanVersionId);
    }
}
