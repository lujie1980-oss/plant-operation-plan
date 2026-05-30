package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;



import java.time.LocalDate;



@Entity

@Table(name = "master_plan_allocation")

public class MasterPlanAllocationEntity extends WorkspaceScopedEntity {



    public String planVersionId;

    public String workOrderNo;

    public String productCode;

    public String salesOrderNo;

    public int salesOrderLineNo;

    public String resourceId;

    public int slotIndex;

    public LocalDate slotDate;

    public String shiftId;

    /** 规划实体 ID，拆段时为 workOrderNo#段号 */

    public String allocationId;

    public Integer durationMinutes;

}


