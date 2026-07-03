package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 external_customer_order_line_delivery → txn_customer_order_line_delivery · TODO-14 */
@Entity
@Table(name = "external_customer_order_line_delivery")
public class ExternalCustomerOrderLineDeliveryEntity extends ExternalStagingEntity {

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

    @Column(name = "line_status", length = 64)
    public String lineStatus;

    public static List<ExternalCustomerOrderLineDeliveryEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalCustomerOrderLineDeliveryEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
