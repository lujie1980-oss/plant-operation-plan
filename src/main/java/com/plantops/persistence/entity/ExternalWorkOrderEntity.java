package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 external_work_order → txn_supply_order · TODO-14 */
@Entity
@Table(name = "external_work_order")
public class ExternalWorkOrderEntity extends ExternalStagingEntity {

    @Column(name = "work_order_no", length = 128)
    public String workOrderNo;

    @Column(name = "product_code", length = 128)
    public String productCode;

    public BigDecimal quantity;

    @Column(name = "need_date")
    public LocalDate needDate;

    @Column(name = "parent_work_order_no", length = 128)
    public String parentWorkOrderNo;

    @Column(name = "firm_flag")
    public boolean firmFlag;

    @Column(name = "source_type", length = 64)
    public String sourceType;

    @Column(name = "dispatch_status", length = 64)
    public String dispatchStatus;

    public static List<ExternalWorkOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalWorkOrderEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
