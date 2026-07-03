package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.List;

/** §12 txn_operation · TODO-14 */
@Entity
@Table(name = "txn_operation", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "operation_id"
}))
public class TxnOperationEntity extends WorkspaceScopedEntity {

    @Column(name = "operation_id", length = 128)
    public String operationId;

    @Column(name = "supply_order_id", length = 128)
    public String supplyOrderId;

    @Column(name = "plan_unit_id", length = 128)
    public String planUnitId;

    @Column(name = "routing_sequence_no")
    public int routingSequenceNo;

    @Column(name = "operation_code", length = 128)
    public String operationCode;

    @Column(name = "operation_name", length = 256)
    public String operationName;

    @Column(name = "planned_start")
    public LocalDateTime plannedStart;

    @Column(name = "planned_end")
    public LocalDateTime plannedEnd;

    public static List<TxnOperationEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
