package com.plantops.scenario.rules;

import java.util.List;

/**
 * 业务规则版本快照（产能换型、物料关键件、需求优先级等）。
 */
public record RuleSetSnapshot(
        List<ChangeoverRule> changeovers,
        List<BomRule> bomRules,
        List<DemandRule> demandRules) {

    public record ChangeoverRule(
            String operationName,
            String attributeKey,
            String fromAttributeValue,
            String toAttributeValue,
            int setupMinutes) {
    }

    public record BomRule(
            String finishedProductCode,
            String parentProductCode,
            String componentProductCode,
            boolean isCriticalComponent) {
    }

    public record DemandRule(
            String salesOrderNo,
            int salesOrderLineNo,
            int priority,
            int expediteLevel,
            boolean scheduleLockFlag) {
    }
}
