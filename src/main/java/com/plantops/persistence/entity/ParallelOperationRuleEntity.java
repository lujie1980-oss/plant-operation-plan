package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "parallel_operation_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "line_id", "first_product_code", "second_product_code"
}))
public class ParallelOperationRuleEntity extends WorkspaceScopedEntity {

    /** 产线 ID（线体 / 机台，如 YD-13） */
    @Column(name = "line_id")
    public String lineId;

    @Column(name = "first_product_code")
    public String firstProductCode;

    @Column(name = "second_product_code")
    public String secondProductCode;

    public static java.util.List<ParallelOperationRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static ParallelOperationRuleEntity findEntry(
            String lineId, String firstProductCode, String secondProductCode) {
        return find(
                "workspaceId = ?1 and lineId = ?2 and firstProductCode = ?3 and secondProductCode = ?4",
                ws(), lineId, firstProductCode, secondProductCode).firstResult();
    }

    public static java.util.List<ParallelOperationRuleEntity> findByLineId(String lineId) {
        return list("workspaceId = ?1 and lineId = ?2 order by firstProductCode, secondProductCode",
                ws(), lineId);
    }

    public static java.util.List<ParallelOperationRuleEntity> findByProduct(String productCode) {
        return list(
                "workspaceId = ?1 and (firstProductCode = ?2 or secondProductCode = ?2)",
                ws(), productCode);
    }
}
