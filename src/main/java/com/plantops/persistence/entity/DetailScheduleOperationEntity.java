package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



@Entity

@Table(name = "detail_schedule_operation")

public class DetailScheduleOperationEntity extends WorkspaceScopedEntity {



    public String planVersionId;

    public String operationId;

    public String workOrderNo;

    public String lineId;

    public int sequenceIndex;

    public int startMinute;

    public int endMinute;

    public boolean pinned;

}


