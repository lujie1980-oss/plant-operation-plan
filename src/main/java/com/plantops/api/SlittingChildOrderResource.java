package com.plantops.api;

import com.plantops.api.dto.slitting.ChildSlittingOrderDto;
import com.plantops.api.dto.slitting.ImportChildOrdersFromDemandRequest;
import com.plantops.api.dto.slitting.ImportChildOrdersFromDemandResult;
import com.plantops.scenario.slitting.ChildSlittingOrderService;
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

import java.util.List;

@Path("/api/v1/slitting/child-orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingChildOrderResource {

    @Inject
    ChildSlittingOrderService childSlittingOrderService;

    @GET
    public List<ChildSlittingOrderDto> list() {
        return childSlittingOrderService.list();
    }

    @POST
    public ChildSlittingOrderDto create(ChildSlittingOrderDto dto) {
        return childSlittingOrderService.create(dto);
    }

    @PUT
    @Path("/{orderCode}")
    public ChildSlittingOrderDto update(@PathParam("orderCode") String orderCode, ChildSlittingOrderDto dto) {
        return childSlittingOrderService.update(orderCode, dto);
    }

    @DELETE
    @Path("/{orderCode}")
    public void delete(@PathParam("orderCode") String orderCode) {
        childSlittingOrderService.archive(orderCode);
    }

    @POST
    @Path("/from-demand")
    public ImportChildOrdersFromDemandResult fromDemand(ImportChildOrdersFromDemandRequest request) {
        return childSlittingOrderService.importFromDemand(request);
    }
}
