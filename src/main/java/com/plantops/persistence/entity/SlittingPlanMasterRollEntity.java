package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

@Entity
@Table(name = "slitting_plan_master_roll")
public class SlittingPlanMasterRollEntity extends WorkspaceScopedEntity {

    @Column(name = "plan_version_id", nullable = false, length = 64)
    public String planVersionId;

    @Column(name = "master_roll_id", nullable = false)
    public Long masterRollId;

    public static List<SlittingPlanMasterRollEntity> listByPlanVersionId(String planVersionId) {
        return list("workspaceId = ?1 and planVersionId = ?2", ws(), planVersionId);
    }
}
