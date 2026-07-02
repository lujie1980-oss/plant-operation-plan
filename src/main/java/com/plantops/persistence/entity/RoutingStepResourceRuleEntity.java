package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "routing_step_resource_rule", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "standard_resource_code"
}))
public class RoutingStepResourceRuleEntity extends WorkspaceScopedEntity {

    public String standardResourceCode;

    public int resourcePriority = 1;

    public BigDecimal productionRate = BigDecimal.ONE;

    public String resourceUsageType = "SINGLE";

    public int batchSize = 1;

    public int batchDurationMinutes;

    public static List<RoutingStepResourceRuleEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }

    public static RoutingStepResourceRuleEntity findByResourceCode(String standardResourceCode) {
        return find("workspaceId = ?1 and standardResourceCode = ?2", ws(), standardResourceCode)
                .firstResult();
    }
}
