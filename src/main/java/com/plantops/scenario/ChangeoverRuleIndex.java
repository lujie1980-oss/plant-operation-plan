package com.plantops.scenario;

import com.plantops.persistence.entity.ChangeoverMatrixEntity;

import java.util.List;

/** 详细排程求解期内使用的换型规则快照（无 DB 依赖）。 */
public final class ChangeoverRuleIndex {

    public record Rule(
            String operationName,
            String attributeKey,
            String fromAttributeValue,
            String toAttributeValue,
            int setupMinutes) {
    }

    private final List<Rule> rules;
    private final ChangeoverDurationService durationService = new ChangeoverDurationService();

    public ChangeoverRuleIndex(List<Rule> rules) {
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public static ChangeoverRuleIndex fromWorkspace() {
        List<Rule> rules = ChangeoverMatrixEntity.listInWorkspace().stream()
                .map(e -> new Rule(
                        e.operationName,
                        e.attributeKey,
                        e.fromAttributeValue,
                        e.toAttributeValue,
                        e.setupMinutes))
                .toList();
        return new ChangeoverRuleIndex(rules);
    }

    public int computeMinutes(String operationName, String previousProductCode, String nextProductCode) {
        if (previousProductCode == null
                || nextProductCode == null
                || previousProductCode.equals(nextProductCode)
                || operationName == null
                || operationName.isBlank()) {
            return 0;
        }
        int total = 0;
        String op = operationName.trim();
        for (Rule rule : rules) {
            if (!op.equals(rule.operationName())) {
                continue;
            }
            String fromVal = durationService.resolveAttributeValue(
                    previousProductCode, op, rule.attributeKey());
            String toVal = durationService.resolveAttributeValue(
                    nextProductCode, op, rule.attributeKey());
            ChangeoverMatrixEntity probe = new ChangeoverMatrixEntity();
            probe.fromAttributeValue = rule.fromAttributeValue();
            probe.toAttributeValue = rule.toAttributeValue();
            if (ChangeoverDurationService.ruleMatches(probe, fromVal, toVal)) {
                total += Math.max(0, rule.setupMinutes());
            }
        }
        return total;
    }
}
