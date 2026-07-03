package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.List;

/** §12 external_customer_order → txn_customer_order · TODO-14 */
@Entity
@Table(name = "external_customer_order")
public class ExternalCustomerOrderEntity extends ExternalStagingEntity {

    @Column(name = "customer_order_no", length = 128)
    public String customerOrderNo;

    @Column(name = "customer_code", length = 128)
    public String customerCode;

    @Column(name = "order_date")
    public LocalDate orderDate;

    @Column(name = "order_status", length = 64)
    public String orderStatus;

    @Column(name = "customer_grade", length = 32)
    public String customerGrade;

    public Integer priority;

    @Column(name = "kitting_enabled")
    public Boolean kittingEnabled;

    @Column(name = "kitting_granularity", length = 32)
    public String kittingGranularity;

    public static List<ExternalCustomerOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalCustomerOrderEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
