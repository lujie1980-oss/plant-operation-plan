package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 txn_purchase_order · TODO-14 */
@Entity
@Table(name = "txn_purchase_order", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "purchase_order_no", "line_no"
}))
public class TxnPurchaseOrderEntity extends WorkspaceScopedEntity {

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

    @Column(name = "available_date")
    public LocalDate availableDate;

    @Column(length = 64)
    public String status;

    public static List<TxnPurchaseOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
