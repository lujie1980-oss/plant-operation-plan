package com.plantops.api;

import com.plantops.api.dto.batch.BatchCancelRequestDto;
import com.plantops.api.dto.batch.BatchPendingScheduleEligibleRequestDto;
import com.plantops.api.dto.batch.BatchPlanWorkOrderDto;
import com.plantops.api.dto.batch.BatchSplitResultDto;
import com.plantops.api.dto.batch.BulkBatchSplitResultDto;
import com.plantops.api.dto.batch.InventoryBatchAllocationDto;
import com.plantops.api.dto.batch.ManualBatchCreateRequestDto;
import com.plantops.api.dto.batch.ProductionBatchDto;
import com.plantops.api.dto.batch.ProductionBatchKittingDto;
import com.plantops.scenario.WorkOrderService;
import com.plantops.scenario.batch.ProductionBatchKittingService;
import com.plantops.scenario.batch.ProductionBatchSplitService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/scheduling/batches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchedulingBatchResource {

    @Inject
    ProductionBatchSplitService batchSplitService;

    @Inject
    ProductionBatchKittingService batchKittingService;

    @Inject
    WorkOrderService workOrderService;

    @GET
    @Path("/work-orders")
    public List<BatchPlanWorkOrderDto> listWorkOrders() {
        return batchSplitService.listWorkOrdersForBatchPlan();
    }

    @GET
    @Path("/by-work-order/{workOrderNo}")
    public List<ProductionBatchDto> listByWorkOrder(@PathParam("workOrderNo") String workOrderNo) {
        return batchSplitService.listBatches(workOrderNo);
    }

    @POST
    @Path("/split/auto")
    public BatchSplitResultDto autoSplit(ManualBatchCreateRequestDto request) {
        return batchSplitService.autoSplit(request.workOrderNo());
    }

    @POST
    @Path("/split/auto-all")
    public BulkBatchSplitResultDto autoSplitAll() {
        return batchSplitService.autoSplitAll();
    }

    @POST
    @Path("/split/manual")
    public BatchSplitResultDto manualSplit(ManualBatchCreateRequestDto request) {
        return batchSplitService.manualSplit(request.workOrderNo(), request.quantity());
    }

    @POST
    @Path("/cancel")
    public BatchSplitResultDto cancel(BatchCancelRequestDto request) {
        return batchSplitService.cancel(request);
    }

    @POST
    @Path("/refresh-kitting")
    public BatchSplitResultDto refreshKitting(ManualBatchCreateRequestDto request) {
        return batchSplitService.refreshKittingStatuses(request.workOrderNo());
    }

    @GET
    @Path("/kitting")
    public List<ProductionBatchKittingDto> listBatchKitting() {
        return batchKittingService.listPendingBatchKitting();
    }

    @POST
    @Path("/kitting/compute")
    public List<ProductionBatchKittingDto> computeBatchKitting() {
        return batchKittingService.recomputeAllPendingBatchKitting();
    }

    @GET
    @Path("/kitting/component/{productCode}/allocations")
    public List<InventoryBatchAllocationDto> batchAllocations(
            @PathParam("productCode") String productCode) {
        return batchKittingService.batchesUsingComponent(productCode);
    }

    @PATCH
    @Path("/{batchNo}/pending-schedule-eligible")
    public ProductionBatchKittingDto updateBatchPendingScheduleEligiblePatch(
            @PathParam("batchNo") String batchNo,
            BatchPendingScheduleEligibleRequestDto request) {
        return updateBatchPendingScheduleEligiblePut(batchNo, request);
    }

    @PUT
    @Path("/{batchNo}/pending-schedule-eligible")
    public ProductionBatchKittingDto updateBatchPendingScheduleEligiblePut(
            @PathParam("batchNo") String batchNo,
            BatchPendingScheduleEligibleRequestDto request) {
        if (request == null) {
            throw new jakarta.ws.rs.BadRequestException("请求体不能为空");
        }
        return batchKittingService.updatePendingScheduleEligible(
                batchNo, request.pendingScheduleEligible());
    }

    @GET
    @Path("/{batchNo}/routing")
    public com.plantops.api.dto.WorkOrderRoutingDetailDto routing(
            @PathParam("batchNo") String batchNo,
            @jakarta.ws.rs.QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        var batch = com.plantops.persistence.entity.ProductionBatchEntity.findByBatchNo(batchNo);
        if (batch == null || !com.plantops.persistence.entity.ProductionBatchEntity.STATUS_ACTIVE.equals(batch.status)) {
            throw new jakarta.ws.rs.NotFoundException("批次不存在: " + batchNo);
        }
        return workOrderService.routingDetailForQuantity(
                batch.workOrderNo, batch.quantity, masterPlanVersionId, batchNo);
    }
}
