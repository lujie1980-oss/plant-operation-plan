package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §12 external_customer_order_line → txn_customer_order_line · TODO-14 */
@Entity
@Table(name = "external_customer_order_line")
public class ExternalCustomerOrderLineEntity extends ExternalStagingEntity {

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

    public static List<ExternalCustomerOrderLineEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalCustomerOrderLineEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
