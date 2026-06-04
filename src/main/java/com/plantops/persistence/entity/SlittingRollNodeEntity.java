package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "slitting_roll_node", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "plan_version_id", "node_id"
}))
public class SlittingRollNodeEntity extends WorkspaceScopedEntity {

    @Column(name = "plan_version_id", nullable = false, length = 64)
    public String planVersionId;

    @Column(name = "node_id", nullable = false, length = 64)
    public String nodeId;

    @Column(name = "node_type", nullable = false, length = 32)
    public String nodeType;

    @Column(name = "parent_node_id", length = 64)
    public String parentNodeId;

    @Column(name = "width_mm", nullable = false)
    public BigDecimal widthMm;

    @Column(name = "length_mm", nullable = false)
    public BigDecimal lengthMm;

    @Column(name = "thickness_mm")
    public BigDecimal thicknessMm;

    @Column(name = "cutting_method", length = 32)
    public String cuttingMethod;

    @Column(name = "kerf_mm")
    public BigDecimal kerfMm;

    @Column(name = "source_spec_code", length = 128)
    public String sourceSpecCode;

    @Column(name = "source_child_order_id")
    public Long sourceChildOrderId;

    @Column(name = "source_master_roll_id")
    public Long sourceMasterRollId;

    public static List<SlittingRollNodeEntity> listByPlanVersionId(String planVersionId) {
        return list("workspaceId = ?1 and planVersionId = ?2 order by nodeId", ws(), planVersionId);
    }

    public static void deleteByPlanVersionId(String planVersionId) {
        delete("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId);
    }
}
