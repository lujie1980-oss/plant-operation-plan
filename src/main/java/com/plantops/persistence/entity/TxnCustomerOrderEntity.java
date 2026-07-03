package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.List;

/** §12 txn_customer_order · TODO-14 */
@Entity
@Table(name = "txn_customer_order", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "customer_order_no"
}))
public class TxnCustomerOrderEntity extends WorkspaceScopedEntity {

    @Column(name = "customer_order_no", length = 128)
    public String customerOrderNo;

    @Column(name = "customer_code", length = 128)
    public String customerCode;

    @Column(name = "order_date")
    public LocalDate orderDate;

    @Column(name = "source_status", length = 64)
    public String sourceStatus;

    @Column(name = "customer_grade", length = 32)
    public String customerGrade;

    public Integer priority;

    @Column(name = "kitting_enabled")
    public Boolean kittingEnabled;

    @Column(name = "kitting_granularity", length = 32)
    public String kittingGranularity;

    public static List<TxnCustomerOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
