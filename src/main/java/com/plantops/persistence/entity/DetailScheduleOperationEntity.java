package com.plantops.persistence.entity;



import jakarta.persistence.Entity;

import jakarta.persistence.Table;

import java.util.List;



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

    /** 排程批次号（拆批后 S05 最小单位）。 */
    public String batchNo;

    public static List<DetailScheduleOperationEntity> findByPlanAndWorkOrder(
            String planVersionId, String workOrderNo) {
        return list(
                "workspaceId = ?1 and planVersionId = ?2 and workOrderNo = ?3",
                ws(),
                planVersionId,
                workOrderNo);
    }

}
