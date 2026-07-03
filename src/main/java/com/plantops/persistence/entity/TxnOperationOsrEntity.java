package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §12 txn_operation_osr · TODO-14 */
@Entity
@Table(name = "txn_operation_osr", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "operation_id", "standard_resource_code"
}))
public class TxnOperationOsrEntity extends WorkspaceScopedEntity {

    @Column(name = "operation_id", length = 128)
    public String operationId;

    @Column(name = "supply_order_id", length = 128)
    public String supplyOrderId;

    @Column(name = "standard_resource_code", length = 128)
    public String standardResourceCode;

    @Column(name = "resource_priority")
    public int resourcePriority;

    @Column(name = "setup_time_minutes")
    public int setupTimeMinutes;

    @Column(name = "process_time_seconds")
    public BigDecimal processTimeSeconds;

    public static List<TxnOperationOsrEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
