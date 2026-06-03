package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "production_task", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "step_id"
}))
public class ProductionTaskEntity extends WorkspaceScopedEntity {

    public String stepId;
    public String batchNo;
    public String workOrderNo;
    public int operationSeq;
    public String operationName;
    public String productCode;
    public String lineId;
    public String resourceId;
    public BigDecimal quantity;
    public LocalDateTime plannedStartTs;
    public LocalDateTime plannedEndTs;
    public String planVersionId;
    public String executionState = "UNPLANNED";
    public LocalDateTime releasedTs;
    public LocalDateTime actualStartTs;
    public LocalDateTime actualEndTs;
    public LocalDateTime updatedTs = LocalDateTime.now();

    public static ProductionTaskEntity findByStepId(String stepId) {
        return find("workspaceId = ?1 and stepId = ?2", ws(), stepId).firstResult();
    }

    public static List<ProductionTaskEntity> listAllOrdered() {
        return list(
                "workspaceId = ?1 order by plannedStartTs nulls last, workOrderNo, operationSeq",
                ws());
    }

    public static List<ProductionTaskEntity> listByState(String executionState) {
        return list(
                "workspaceId = ?1 and executionState = ?2 order by plannedStartTs nulls last, workOrderNo, operationSeq",
                ws(),
                executionState);
    }
}
