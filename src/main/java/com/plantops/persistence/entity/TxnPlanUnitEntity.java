package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

/** §12 txn_plan_unit · TODO-14 */
@Entity
@Table(name = "txn_plan_unit", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "plan_unit_id"
}))
public class TxnPlanUnitEntity extends WorkspaceScopedEntity {

    @Column(name = "plan_unit_id", length = 128)
    public String planUnitId;

    @Column(name = "supply_order_id", length = 128)
    public String supplyOrderId;

    public BigDecimal quantity;

    @Column(name = "sequence_no")
    public int sequenceNo;

    public static List<TxnPlanUnitEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
