package com.plantops.api;

import com.plantops.api.dto.CustomerOrderLineDeliveryListItemDto;
import com.plantops.api.dto.DemandPoolSummaryDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.demand.OrderDemandActionRequest;
import com.plantops.api.dto.demand.PromiseDatePreviewDto;
import com.plantops.api.dto.demand.OrderDemandActionResult;
import com.plantops.ontology.demand.CustomerOrderLine;
import com.plantops.scenario.OrderDemandAction;
import com.plantops.scenario.OrderDemandActionService;
import com.plantops.scenario.OntologyFulfillmentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/v1/ontology/fulfillment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OntologyFulfillmentResource {

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    OrderDemandActionService orderDemandActionService;

    @GET
    @Path("/deliveries")
    public List<CustomerOrderLineDeliveryListItemDto> deliveries(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyFulfillmentService.listDeliveries(masterPlanVersionId);
    }

    @GET
    @Path("/deliveries/summary")
    public DemandPoolSummaryDto deliverySummary(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyFulfillmentService.deliverySummary(masterPlanVersionId);
    }

    @GET
    @Path("/deliveries/{deliveryId}/promise-date-preview")
    public PromiseDatePreviewDto promiseDatePreview(
            @PathParam("deliveryId") String deliveryId,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        CustomerOrderLine line = ontologyFulfillmentService.requireCustomerOrderLine(
                deliveryId, masterPlanVersionId);
        OrderDemandActionRequest req = new OrderDemandActionRequest(
                masterPlanVersionId,
                null,
                null,
                null);
        return orderDemandActionService.previewPromiseDate(
                line.getSalesOrderNo(),
                line.getSalesOrderLineNo(),
                req);
    }

    @GET
    @Path("/deliveries/{deliveryId}/fulfillment-chain")
    public OrderFulfillmentChainDto fulfillmentChain(
            @PathParam("deliveryId") String deliveryId,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyFulfillmentService.fulfillmentChain(deliveryId, masterPlanVersionId);
    }

    @GET
    @Path("/supply-orders/{workOrderNo}/upstream-chain")
    public OrderFulfillmentChainDto supplyOrderUpstreamChain(
            @PathParam("workOrderNo") String workOrderNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyFulfillmentService.supplyOrderUpstreamChain(workOrderNo, masterPlanVersionId);
    }

    @GET
    @Path("/supply-orders/{workOrderNo}/downstream-chain")
    public OrderFulfillmentChainDto supplyOrderDownstreamChain(
            @PathParam("workOrderNo") String workOrderNo,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return ontologyFulfillmentService.supplyOrderDownstreamChain(workOrderNo, masterPlanVersionId);
    }

    @POST
    @Path("/deliveries/{deliveryId}/actions/{action}")
    public OrderDemandActionResult deliveryAction(
            @PathParam("deliveryId") String deliveryId,
            @PathParam("action") String action,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId,
            OrderDemandActionRequest body) {
        CustomerOrderLine line = ontologyFulfillmentService.requireCustomerOrderLine(
                deliveryId, masterPlanVersionId);
        OrderDemandActionRequest req = body != null ? body : new OrderDemandActionRequest(null, null, null, null);
        if (req.masterPlanVersionId() == null && masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            req = new OrderDemandActionRequest(
                    masterPlanVersionId,
                    req.promiseDateOverride(),
                    req.useFeedbackOverlay(),
                    req.feedbackCutoff());
        }
        return orderDemandActionService.execute(
                line.getSalesOrderNo(),
                line.getSalesOrderLineNo(),
                OrderDemandAction.parse(action),
                req);
    }
}
