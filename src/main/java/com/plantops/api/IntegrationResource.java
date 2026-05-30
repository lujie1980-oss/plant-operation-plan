package com.plantops.api;

import com.plantops.integration.erp.ErpPort;
import com.plantops.integration.mes.MesPort;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/integration")
@Produces(MediaType.APPLICATION_JSON)
public class IntegrationResource {

    @Inject
    ErpPort erpPort;

    @Inject
    MesPort mesPort;

    @GET
    @Path("/erp/orders")
    public Object erpOrders() {
        return erpPort.fetchOpenOrderLines();
    }

    @GET
    @Path("/mes/status")
    public Object mesStatus() {
        return java.util.Map.of("adapter", "mock", "feedback", mesPort.pollFeedback());
    }
}
