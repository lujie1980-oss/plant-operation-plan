package com.plantops.api;

import com.plantops.api.dto.slitting.IntermediateRollCatalogDto;
import com.plantops.scenario.slitting.IntermediateCatalogService;
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

@Path("/api/v1/slitting/intermediate-catalog")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingIntermediateCatalogResource {

    @Inject
    IntermediateCatalogService intermediateCatalogService;

    @GET
    public List<IntermediateRollCatalogDto> list() {
        return intermediateCatalogService.list();
    }

    @POST
    public IntermediateRollCatalogDto create(IntermediateRollCatalogDto dto) {
        return intermediateCatalogService.create(dto);
    }

    @PUT
    @Path("/{specCode}")
    public IntermediateRollCatalogDto update(@PathParam("specCode") String specCode, IntermediateRollCatalogDto dto) {
        return intermediateCatalogService.update(specCode, dto);
    }

    @DELETE
    @Path("/{specCode}")
    public void delete(@PathParam("specCode") String specCode) {
        intermediateCatalogService.delete(specCode);
    }
}
