package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** §12 txn_demand · TODO-14 */
@Entity
@Table(name = "txn_demand", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "demand_id"
}))
public class TxnDemandEntity extends WorkspaceScopedEntity {

    @Column(name = "demand_id", length = 128)
    public String demandId;

    @Column(name = "product_code", length = 128)
    public String productCode;

    @Column(name = "stocking_point_code", length = 128)
    public String stockingPointCode;

    public BigDecimal quantity;

    @Column(name = "need_date")
    public LocalDate needDate;

    public Integer priority;

    @Column(name = "source_type", length = 64)
    public String sourceType;

    @Column(name = "source_id", length = 128)
    public String sourceId;

    public static List<TxnDemandEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
