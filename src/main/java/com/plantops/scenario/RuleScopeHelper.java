package com.plantops.scenario;

import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.BomComponentEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 结合规则项目启用范围与 BOM 关键件标记。 */
@ApplicationScoped
public class RuleScopeHelper {

    @Inject
    BusinessRuleScopeService ruleScope;

    public boolean criticalForMasterPlan(BomComponentEntity bom) {
        return ruleScope.isMasterPlanEnabled(BusinessRuleTypeIds.BOM_RULES)
                && bom != null
                && bom.isCriticalComponent;
    }

    public boolean criticalForDetailSchedule(BomComponentEntity bom) {
        return ruleScope.isDetailScheduleEnabled(BusinessRuleTypeIds.BOM_RULES)
                && bom != null
                && bom.isCriticalComponent;
    }
}
