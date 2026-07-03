package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.1 external_product_in_stocking_point → md_pisp */
@Entity
@Table(name = "external_product_in_stocking_point")
public class ExternalProductInStockingPointEntity extends ExternalStagingEntity {

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    @Column(name = "planning_relevant")
    public boolean planningRelevant;

    public BigDecimal ppq;

    @Column(name = "lot_size")
    public BigDecimal lotSize;

    @Column(name = "min_quantity")
    public BigDecimal minQuantity;

    @Column(name = "max_quantity")
    public BigDecimal maxQuantity;

    @Column(name = "min_qty_strategy", length = 32)
    public String minQtyStrategy;

    @Column(name = "procurement_type", length = 32)
    public String procurementType;

    public static List<ExternalProductInStockingPointEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static List<ExternalProductInStockingPointEntity> listForBatch(String importBatchId) {
        return list("workspaceId = ?1 and importBatchId = ?2", ws(), importBatchId);
    }
}
