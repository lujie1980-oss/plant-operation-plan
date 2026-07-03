package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 txn_supply_order · TODO-14 */
@Entity
@Table(name = "txn_supply_order", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "supply_order_id"
}))
public class TxnSupplyOrderEntity extends WorkspaceScopedEntity {

    public static final String FIRM_STATUS_FIRM = "FIRM";
    public static final String FIRM_STATUS_PLANNED = "PLANNED";

    @Column(name = "supply_order_id", length = 128)
    public String supplyOrderId;

    @Column(name = "product_code", length = 128)
    public String productCode;

    public BigDecimal quantity;

    @Column(name = "need_date")
    public LocalDate needDate;

    @Column(name = "parent_supply_order_id", length = 128)
    public String parentSupplyOrderId;

    @Column(name = "firm_status", length = 32)
    public String firmStatus = FIRM_STATUS_PLANNED;

    @Column(name = "source_type", length = 64)
    public String sourceType;

    @Column(name = "dispatch_status", length = 64)
    public String dispatchStatus;

    public static List<TxnSupplyOrderEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
