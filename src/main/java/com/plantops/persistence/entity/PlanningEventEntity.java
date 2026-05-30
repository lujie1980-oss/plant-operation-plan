package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Lob;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.time.LocalDateTime;



@Entity

@Table(name = "planning_event", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "event_id"

}))

public class PlanningEventEntity extends WorkspaceScopedEntity {



    public String eventId;

    public String eventType;

    public LocalDateTime eventTs;

    @Lob

    public String payloadJson;

    public String rescheduleLevel;

    public boolean processed;

}


