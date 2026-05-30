package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

@Entity
@Table(name = "operation_post_processing_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code", "operation_name"
}))
public class OperationPostProcessingRuleEntity extends WorkspaceScopedEntity {

    @Column(name = "product_code")
    public String productCode;

    @Column(name = "operation_name")
    public String operationName = "*";

    public int postProcessingMinutes;

    public static List<OperationPostProcessingRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static OperationPostProcessingRuleEntity findEntry(String productCode, String operationName) {
        return find(
                "workspaceId = ?1 and productCode = ?2 and operationName = ?3",
                ws(), productCode, operationName).firstResult();
    }
}
