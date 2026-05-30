package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.time.LocalDate;

import java.util.List;



@Entity

@Table(name = "resource_calendar")

public class ResourceCalendarEntity extends WorkspaceScopedEntity {



    public String resourceId;

    public String shiftId;

    public LocalDate calendarDate;

    public int availableCapacityMinutes;

    public int unavailableCapacityMinutes;



    public static List<ResourceCalendarEntity> listInWorkspace() {

        return list("workspaceId", ws());

    }



    public static List<ResourceCalendarEntity> findForResource(String resourceId) {

        return list("workspaceId = ?1 and resourceId = ?2", ws(), resourceId);

    }

}


