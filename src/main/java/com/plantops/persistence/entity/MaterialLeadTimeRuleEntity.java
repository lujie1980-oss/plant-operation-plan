package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

/** 物料最长采购周期（天）。productCode={@code *} 为 RULE-MRP-04 默认最长采购周期。 */
@Entity
@Table(name = "material_lead_time_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code"
}))
public class MaterialLeadTimeRuleEntity extends WorkspaceScopedEntity {

    @Column(name = "product_code")
    public String productCode;

    public int leadTimeDays;

    public static List<MaterialLeadTimeRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static MaterialLeadTimeRuleEntity findByProduct(String productCode) {
        return find("workspaceId = ?1 and productCode = ?2", ws(), productCode).firstResult();
    }
}
