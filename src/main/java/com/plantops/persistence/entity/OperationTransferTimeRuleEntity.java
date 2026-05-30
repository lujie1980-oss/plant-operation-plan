package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "operation_transfer_time_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "product_code", "from_operation_name", "to_operation_name"
}))
public class OperationTransferTimeRuleEntity extends WorkspaceScopedEntity {

    @Column(name = "product_code")
    public String productCode;

    @Column(name = "from_operation_name")
    public String fromOperationName;

    @Column(name = "to_operation_name")
    public String toOperationName;

    public int transferMinutes;

    public int minTransferMinutes;

    public static java.util.List<OperationTransferTimeRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static OperationTransferTimeRuleEntity findEntry(
            String productCode, String fromOperationName, String toOperationName) {
        return find(
                "workspaceId = ?1 and productCode = ?2 and fromOperationName = ?3 and toOperationName = ?4",
                ws(), productCode, fromOperationName, toOperationName).firstResult();
    }
}
