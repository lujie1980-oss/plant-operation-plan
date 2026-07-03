package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §11.3.1 md_pisp */
@Entity
@Table(name = "md_pisp", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code", "stocking_point_code"
}))
public class MdPispEntity extends WorkspaceScopedEntity {

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

    public static List<MdPispEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
