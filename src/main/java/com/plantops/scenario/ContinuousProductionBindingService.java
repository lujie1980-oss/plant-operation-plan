package com.plantops.scenario;

import com.plantops.persistence.entity.ContinuousProductionRuleEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.masterdata.BusinessRuleTypeIds;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * 连续生产规则绑定：指定机台上的关联料号须连续排产，中间不得插入其它料号。
 */
@ApplicationScoped
public class ContinuousProductionBindingService {

    @Inject
    BusinessRuleScopeService ruleScope;

    public void applyBindings(List<OperationAssignment> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        if (!ruleScope.isDetailScheduleEnabled(BusinessRuleTypeIds.CONTINUOUS_PRODUCTION)) {
            return;
        }
        for (ContinuousProductionRuleEntity rule : ContinuousProductionRuleEntity.listInWorkspace()) {
            ProductionLineEntity line = ProductionLineEntity.findByLineId(rule.lineId);
            if (line == null || line.resourceId == null || line.resourceId.isBlank()) {
                continue;
            }
            String lineResourceId = line.resourceId;
            String groupId = "CP-" + rule.id + "-" + rule.lineId;
            for (OperationAssignment op : operations) {
                if (!lineResourceId.equals(op.getResourceId())) {
                    continue;
                }
                if (!rule.matchesProduct(op.getProductCode())) {
                    continue;
                }
                if (op.getContinuousGroupId() != null) {
                    continue;
                }
                op.setContinuousProduction(true);
                op.setContinuousGroupId(groupId);
                op.setDesignatedLineId(rule.lineId);
                op.setAllowedLineIds(List.of(rule.lineId));
            }
        }
    }
}
