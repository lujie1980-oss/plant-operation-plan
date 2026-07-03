package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §12 txn_customer_order_line · TODO-14 */
@Entity
@Table(name = "txn_customer_order_line", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "customer_order_no", "line_no"
}))
public class TxnCustomerOrderLineEntity extends WorkspaceScopedEntity {

    @Column(name = "customer_order_no", length = 128)
    public String customerOrderNo;

    @Column(name = "line_no")
    public int lineNo;

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "order_qty")
    public BigDecimal orderQty;

    @Column(name = "uom_code", length = 32)
    public String uomCode;

    public static List<TxnCustomerOrderLineEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
