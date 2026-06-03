package com.plantops.api;

import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarSyncResultDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarDayDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarMonthDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryCalendarPolicyDto;
import com.plantops.api.dto.FactoryCalendarDtos.FactoryDayOverrideRequest;
import com.plantops.masterdata.FactoryCalendarService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/factory-calendar")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FactoryCalendarResource {

    @Inject
    FactoryCalendarService factoryCalendarService;

    @GET
    @Path("/policy")
    public FactoryCalendarPolicyDto getPolicy() {
        return factoryCalendarService.getPolicy();
    }

    @PUT
    @Path("/policy")
    public FactoryCalendarPolicyDto savePolicy(FactoryCalendarPolicyDto dto) {
        return factoryCalendarService.savePolicy(dto);
    }

    @GET
    @Path("/month")
    public FactoryCalendarMonthDto getMonth(
            @QueryParam("year") int year,
            @QueryParam("month") int month) {
        if (year < 2000 || month < 1 || month > 12) {
            var now = java.time.LocalDate.now();
            year = now.getYear();
            month = now.getMonthValue();
        }
        return factoryCalendarService.getMonth(year, month);
    }

    @PUT
    @Path("/day")
    public FactoryCalendarDayDto saveDay(FactoryDayOverrideRequest request) {
        return factoryCalendarService.saveDayOverride(request);
    }

    @POST
    @Path("/sync")
    public FactoryCalendarSyncResultDto syncToResourceCalendars() {
        return factoryCalendarService.syncResourceCalendarsToHorizon();
    }
}
