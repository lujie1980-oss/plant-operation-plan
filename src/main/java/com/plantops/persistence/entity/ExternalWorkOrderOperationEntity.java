package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.List;

/** §12 external_work_order_operation → txn_operation · TODO-14 */
@Entity
@Table(name = "external_work_order_operation")
public class ExternalWorkOrderOperationEntity extends ExternalStagingEntity {

    @Column(name = "work_order_no", length = 128)
    public String workOrderNo;

    @Column(name = "operation_seq")
    public int operationSeq;

    @Column(name = "operation_code", length = 128)
    public String operationCode;

    @Column(name = "operation_name", length = 256)
    public String operationName;

    @Column(name = "planned_start")
    public LocalDateTime plannedStart;

    @Column(name = "planned_end")
    public LocalDateTime plannedEnd;

    @Column(name = "plan_unit_seq")
    public int planUnitSeq;

    public static List<ExternalWorkOrderOperationEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalWorkOrderOperationEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
