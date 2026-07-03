package com.plantops.api;

import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.MasterPlanDataModelTreeDto;
import com.plantops.api.dto.masterplan.MasterPlanDataModelDtos.MasterPlanPispRoutingDetailDto;
import com.plantops.scenario.MasterPlanDataModelService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/ontology/master-model")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyMasterModelResource {

    @Inject
    MasterPlanDataModelService masterPlanDataModelService;

    @GET
    @Path("/tree")
    public MasterPlanDataModelTreeDto tree() {
        return masterPlanDataModelService.listTree();
    }

    @GET
    @Path("/pisps/{pispId}/routing")
    public MasterPlanPispRoutingDetailDto routing(@PathParam("pispId") String pispId) {
        return masterPlanDataModelService.routingDetail(pispId);
    }
}
