package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 external_inventory → txn_inventory_balance · TODO-14 */
@Entity
@Table(name = "external_inventory")
public class ExternalInventoryEntity extends ExternalStagingEntity {

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

    public static List<ExternalInventoryEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalInventoryEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
