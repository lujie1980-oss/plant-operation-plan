package com.plantops.scenario;

import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 工序流转 / 衔接规则索引（产品 + 前工序 + 后工序）。 */
public final class OperationTransferTimeIndex {

    public record Rule(
            String productCode,
            String fromOperationName,
            String toOperationName,
            int transferMinutes,
            int minTransferMinutes,
            int maxTransferMinutes,
            OperationLinkMode linkMode,
            int delayStartMinutes) {
    }

    public record ResolvedRule(
            int minTransferMinutes,
            int maxTransferMinutes,
            OperationLinkMode linkMode,
            int delayStartMinutes) {
    }

    private final Map<String, Rule> byKey;

    public OperationTransferTimeIndex(List<Rule> rules) {
        Map<String, Rule> map = new HashMap<>();
        if (rules != null) {
            for (Rule rule : rules) {
                map.put(key(rule.productCode(), rule.fromOperationName(), rule.toOperationName()), rule);
            }
        }
        this.byKey = Map.copyOf(map);
    }

    public static OperationTransferTimeIndex fromWorkspace() {
        List<Rule> rules = OperationTransferTimeRuleEntity.listInWorkspace().stream()
                .map(OperationTransferTimeIndex::toRule)
                .toList();
        return new OperationTransferTimeIndex(rules);
    }

    public static Rule toRule(OperationTransferTimeRuleEntity e) {
        int max = e.maxTransferMinutes > 0 ? e.maxTransferMinutes : e.transferMinutes;
        return new Rule(
                e.productCode,
                e.fromOperationName,
                e.toOperationName,
                e.transferMinutes,
                e.minTransferMinutes,
                max,
                OperationLinkMode.fromDb(e.linkMode),
                e.delayStartMinutes);
    }

    /** @deprecated 使用 {@link #resolve(String, String, String)} 的 min/max */
    @Deprecated
    public int transferMinutes(String productCode, String fromOperationName, String toOperationName) {
        ResolvedRule rule = resolve(productCode, fromOperationName, toOperationName);
        return rule.maxTransferMinutes() > 0 ? rule.maxTransferMinutes() : rule.minTransferMinutes();
    }

    public int minTransferMinutes(String productCode, String fromOperationName, String toOperationName) {
        return resolve(productCode, fromOperationName, toOperationName).minTransferMinutes();
    }

    public int maxTransferMinutes(String productCode, String fromOperationName, String toOperationName) {
        return resolve(productCode, fromOperationName, toOperationName).maxTransferMinutes();
    }

    public ResolvedRule resolve(String productCode, String fromOperationName, String toOperationName) {
        Rule rule = lookup(productCode, fromOperationName, toOperationName);
        if (rule == null) {
            return new ResolvedRule(0, 0, OperationLinkMode.STANDARD, 0);
        }
        return new ResolvedRule(
                Math.max(0, rule.minTransferMinutes()),
                Math.max(0, rule.maxTransferMinutes()),
                rule.linkMode() != null ? rule.linkMode() : OperationLinkMode.STANDARD,
                Math.max(0, rule.delayStartMinutes()));
    }

    public Rule lookup(String productCode, String fromOperationName, String toOperationName) {
        if (productCode == null || fromOperationName == null || toOperationName == null) {
            return null;
        }
        return byKey.get(key(productCode.trim(), fromOperationName.trim(), toOperationName.trim()));
    }

    public static Rule standardRule(
            String productCode,
            String fromOperationName,
            String toOperationName,
            int maxTransferMinutes,
            int minTransferMinutes) {
        return new Rule(
                productCode,
                fromOperationName,
                toOperationName,
                maxTransferMinutes,
                minTransferMinutes,
                maxTransferMinutes,
                OperationLinkMode.STANDARD,
                0);
    }

    private static String key(String productCode, String fromOperationName, String toOperationName) {
        return productCode + "|" + fromOperationName + "->" + toOperationName;
    }
}
