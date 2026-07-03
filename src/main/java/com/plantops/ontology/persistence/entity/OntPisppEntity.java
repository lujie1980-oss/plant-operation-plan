package com.plantops.ontology.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "ont_pispp")
public class OntPisppEntity extends OntRevisionScopedEntity {

    @Column(name = "pisp_id", nullable = false, length = 128)
    public String pispId;

    @Column(name = "period_id", nullable = false, length = 128)
    public String periodId;

    @Column(name = "on_hand", nullable = false)
    public double onHand;

    @Column(name = "planned_supply_total", nullable = false)
    public double plannedSupplyTotal;

    @Column(name = "planned_supply_total_mrp", nullable = false)
    public double plannedSupplyTotalMrp;

    @Column(name = "planned_supply_total_optimized", nullable = false)
    public double plannedSupplyTotalOptimized;

    @Column(name = "planned_demand_quantity_total", nullable = false)
    public double plannedDemandQuantityTotal;

    @Column(name = "inventory_target_quantity", nullable = false)
    public double inventoryTargetQuantity;

    @Column(name = "planned_inventory_level", nullable = false)
    public double plannedInventoryLevel;

    @Column(name = "replenished_inventory_level", nullable = false)
    public double replenishedInventoryLevel;

    @Column(name = "stock_shortage_quantity", nullable = false)
    public double stockShortageQuantity;

    public static List<OntPisppEntity> forRevision(String workspaceId, String revisionId) {
        return list("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
    }
}
