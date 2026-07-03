package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §12 external_work_order_operation_resource → txn_operation_osr · TODO-14 */
@Entity
@Table(name = "external_work_order_operation_resource")
public class ExternalWorkOrderOperationResourceEntity extends ExternalStagingEntity {

    @Column(name = "work_order_no", length = 128)
    public String workOrderNo;

    @Column(name = "operation_seq")
    public int operationSeq;

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "resource_priority")
    public int resourcePriority;

    @Column(name = "setup_time_minutes")
    public int setupTimeMinutes;

    @Column(name = "process_time_seconds")
    public BigDecimal processTimeSeconds;

    public static List<ExternalWorkOrderOperationResourceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalWorkOrderOperationResourceEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
