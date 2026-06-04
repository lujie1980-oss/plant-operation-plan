package com.plantops.api;

import com.plantops.api.dto.slitting.MasterRollDto;
import com.plantops.scenario.slitting.MasterRollService;
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

@Path("/api/v1/slitting/master-rolls")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SlittingMasterRollResource {

    @Inject
    MasterRollService masterRollService;

    @GET
    public List<MasterRollDto> list() {
        return masterRollService.list();
    }

    @POST
    public MasterRollDto create(MasterRollDto dto) {
        return masterRollService.create(dto);
    }

    @PUT
    @Path("/{rollCode}")
    public MasterRollDto update(@PathParam("rollCode") String rollCode, MasterRollDto dto) {
        return masterRollService.update(rollCode, dto);
    }

    @DELETE
    @Path("/{rollCode}")
    public void delete(@PathParam("rollCode") String rollCode) {
        masterRollService.archive(rollCode);
    }
}
