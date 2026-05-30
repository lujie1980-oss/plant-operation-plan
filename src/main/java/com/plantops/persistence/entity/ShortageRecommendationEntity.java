package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Lob;

import jakarta.persistence.Table;

import jakarta.persistence.UniqueConstraint;



import java.time.LocalDateTime;



@Entity

@Table(name = "shortage_recommendation", uniqueConstraints = @UniqueConstraint(columnNames = {

        "workspace_id", "shortage_id"

}))

public class ShortageRecommendationEntity extends WorkspaceScopedEntity {



    public String shortageId;

    public String planVersionId;

    public String shortageType;

    public String severity;

    public String areaId;

    public String shiftId;

    public String lineId;

    @Lob

    public String evidenceJson;

    public String recommendedAction;

    @Lob

    public String impactOrdersJson;

    public LocalDateTime createdTs;

}


