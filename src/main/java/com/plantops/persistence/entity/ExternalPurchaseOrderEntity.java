package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 external_purchase_order → txn_purchase_order · TODO-14 */
@Entity
@Table(name = "external_purchase_order")
public class ExternalPurchaseOrderEntity extends ExternalStagingEntity {

    @Column(name = "purchase_order_no", length = 128)
    public String purchaseOrderNo;

    @Column(name = "line_no")
    public int lineNo;

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "order_qty")
    public BigDecimal orderQty;

    @Column(name = "open_qty")
    public BigDecimal openQty;

    @Column(name = "promised_date")
    public LocalDate promisedDate;

    @Column(name = "po_status", length = 64)
    public String poStatus;

    public static List<ExternalPurchaseOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalPurchaseOrderEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
