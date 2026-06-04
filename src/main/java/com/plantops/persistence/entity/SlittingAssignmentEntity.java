package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "slitting_assignment", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "plan_version_id", "assignment_id"
}))
public class SlittingAssignmentEntity extends WorkspaceScopedEntity {

    @Column(name = "plan_version_id", nullable = false, length = 64)
    public String planVersionId;

    @Column(name = "assignment_id", nullable = false, length = 64)
    public String assignmentId;

    @Column(name = "child_node_id", nullable = false, length = 64)
    public String childNodeId;

    @Column(name = "parent_node_id", nullable = false, length = 64)
    public String parentNodeId;

    @Column(name = "pos_x_mm", nullable = false)
    public BigDecimal posXMm;

    @Column(name = "pos_y_mm", nullable = false)
    public BigDecimal posYMm;

    @Column(name = "rotated", nullable = false)
    public boolean rotated;

    @Column(name = "sequence")
    public Integer sequence;

    public static List<SlittingAssignmentEntity> listByPlanVersionId(String planVersionId) {
        return list("workspaceId = ?1 and planVersionId = ?2 order by sequence, assignmentId", ws(), planVersionId);
    }

    public static void deleteByPlanVersionId(String planVersionId) {
        delete("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId);
    }
}
