package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 txn_customer_order_line_delivery · TODO-14 */
@Entity
@Table(name = "txn_customer_order_line_delivery", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "customer_order_no", "line_no", "delivery_seq"
}))
public class TxnCustomerOrderLineDeliveryEntity extends WorkspaceScopedEntity {

    @Column(name = "customer_order_no", length = 128)
    public String customerOrderNo;

    @Column(name = "line_no")
    public int lineNo;

    @Column(name = "delivery_seq")
    public int deliverySeq;

    @Column(name = "delivery_qty")
    public BigDecimal deliveryQty;

    @Column(name = "delivery_min_qty")
    public BigDecimal deliveryMinQty;

    @Column(name = "delivery_max_qty")
    public BigDecimal deliveryMaxQty;

    public BigDecimal ppq;

    @Column(name = "delivery_granularity", length = 32)
    public String deliveryGranularity;

    @Column(name = "early_allow_days")
    public Integer earlyAllowDays;

    @Column(name = "late_allow_days")
    public Integer lateAllowDays;

    @Column(name = "requested_date")
    public LocalDate requestedDate;

    @Column(name = "confirmed_date")
    public LocalDate confirmedDate;

    @Column(length = 64)
    public String status;

    public static List<TxnCustomerOrderLineDeliveryEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
