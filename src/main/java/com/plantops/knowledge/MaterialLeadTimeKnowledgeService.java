package com.plantops.knowledge;

import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.persistence.entity.MaterialLeadTimeRuleEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** RULE-MRP-04：Custom 表行 + Effective 默认（TODO-15 K3）。 */
@ApplicationScoped
public class MaterialLeadTimeKnowledgeService {

    @Inject
    KnowledgeContext knowledgeContext;

    @Inject
    ParameterRegistry parameters;

    @Inject
    BusinessRuleScopeService ruleScope;

    public int leadTimeDaysForProduct(String productCode) {
        if (!ruleScope.isMasterPlanEnabled(BusinessRuleTypeIds.MATERIAL_LEAD_TIME)) {
            return effectiveDefaultDays();
        }
        if (productCode != null && !"*".equals(productCode)) {
            MaterialLeadTimeRuleEntity exact = MaterialLeadTimeRuleEntity.findByProduct(productCode);
            if (exact != null) {
                return Math.max(0, exact.leadTimeDays);
            }
        }
        MaterialLeadTimeRuleEntity wildcard = MaterialLeadTimeRuleEntity.findByProduct("*");
        if (wildcard != null) {
            return Math.max(0, wildcard.leadTimeDays);
        }
        return effectiveDefaultDays();
    }

    public int effectiveDefaultDays() {
        String tabDefault =
                knowledgeContext.getParameter("business_rules_tabs.material-lead-time.default_lead_time_days");
        if (tabDefault != null && !tabDefault.isBlank()) {
            try {
                return Math.max(0, Integer.parseInt(tabDefault.trim()));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Math.max(0, parameters.getInt("default_procurement_lead_time_days", 7));
    }
}
