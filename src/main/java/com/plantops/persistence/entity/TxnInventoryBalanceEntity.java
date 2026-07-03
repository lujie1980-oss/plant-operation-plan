package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 txn_inventory_balance · TODO-14 */
@Entity
@Table(name = "txn_inventory_balance", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code", "stocking_point_code"
}))
public class TxnInventoryBalanceEntity extends WorkspaceScopedEntity {

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "on_hand_qty")
    public BigDecimal onHandQty;

    @Column(name = "available_qty")
    public BigDecimal availableQty;

    @Column(name = "as_of_date")
    public LocalDate asOfDate;

    public static List<TxnInventoryBalanceEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
