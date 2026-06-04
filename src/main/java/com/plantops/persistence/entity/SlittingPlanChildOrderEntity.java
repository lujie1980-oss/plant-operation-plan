package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "slitting_plan_child_order")
public class SlittingPlanChildOrderEntity extends WorkspaceScopedEntity {

    @Column(name = "plan_version_id", nullable = false, length = 64)
    public String planVersionId;

    @Column(name = "child_slitting_order_id", nullable = false)
    public Long childSlittingOrderId;

    public static List<SlittingPlanChildOrderEntity> listByPlanVersionId(String planVersionId) {
        return list("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId);
    }
}
