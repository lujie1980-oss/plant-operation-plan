package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;

@Entity
@Table(name = "routing_step_timing_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "routing_code", "sequence_no"
}))
public class RoutingStepTimingRuleEntity extends WorkspaceScopedEntity {

    public String routingCode;

    public int sequenceNo;

    public int preProcessingMinutes;

    public int schedulingSpaceMinutes;

    public int productionMinutes;

    public int postProcessingMinutes;

    public static List<RoutingStepTimingRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static RoutingStepTimingRuleEntity findByKey(String routingCode, int sequenceNo) {
        return find(
                        "workspaceId = ?1 and routingCode = ?2 and sequenceNo = ?3",
                        ws(),
                        routingCode,
                        sequenceNo)
                .firstResult();
    }
}
