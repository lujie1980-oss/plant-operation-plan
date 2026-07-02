package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

@Entity
@Table(name = "supply_quantity_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code", "stocking_point_code"
}))
public class SupplyQuantityRuleEntity extends WorkspaceScopedEntity {

    public String productCode;

    public String stockingPointCode;

    public int lotSize = 1;

    public int minQuantity = 1;

    public int maxQuantity = 99999;

    public String minQtyStrategy = "PLAN_AT_MIN";

    public static List<SupplyQuantityRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static SupplyQuantityRuleEntity findByKey(String productCode, String stockingPointCode) {
        return find(
                        "workspaceId = ?1 and productCode = ?2 and stockingPointCode = ?3",
                        ws(),
                        productCode,
                        stockingPointCode)
                .firstResult();
    }
}
