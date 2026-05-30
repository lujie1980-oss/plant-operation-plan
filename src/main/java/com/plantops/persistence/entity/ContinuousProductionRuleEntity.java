package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "continuous_production_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "line_id", "first_product_code", "second_product_code", "finished_product_code"
}))
public class ContinuousProductionRuleEntity extends WorkspaceScopedEntity {

    @Column(name = "line_id")
    public String lineId;

    @Column(name = "first_product_code")
    public String firstProductCode = "";

    @Column(name = "second_product_code")
    public String secondProductCode = "";

    @Column(name = "finished_product_code")
    public String finishedProductCode = "";

    public static java.util.List<ContinuousProductionRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static ContinuousProductionRuleEntity findEntry(
            String lineId, String firstProductCode, String secondProductCode, String finishedProductCode) {
        return find(
                "workspaceId = ?1 and lineId = ?2 and firstProductCode = ?3 "
                        + "and secondProductCode = ?4 and finishedProductCode = ?5",
                ws(),
                lineId,
                normalizeCode(firstProductCode),
                normalizeCode(secondProductCode),
                normalizeCode(finishedProductCode)).firstResult();
    }

    public static String normalizeCode(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public boolean matchesProduct(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        String code = productCode.trim();
        return code.equals(firstProductCode)
                || code.equals(secondProductCode)
                || code.equals(finishedProductCode);
    }
}
