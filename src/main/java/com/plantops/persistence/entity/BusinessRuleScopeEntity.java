package com.plantops.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "business_rule_scope", uniqueConstraints = @UniqueConstraint(columnNames = {
        "workspace_id", "rule_type_id"
}))
public class BusinessRuleScopeEntity extends WorkspaceScopedEntity {

    public String ruleTypeId;

    public boolean enableMasterPlan = true;

    public boolean enableDetailSchedule = true;

    public static BusinessRuleScopeEntity findByRuleType(String ruleTypeId) {
        return find("workspaceId = ?1 and ruleTypeId = ?2", ws(), ruleTypeId).firstResult();
    }

    public static java.util.List<BusinessRuleScopeEntity> listInWorkspace() {
        return list("workspaceId", ws());
    }
}
