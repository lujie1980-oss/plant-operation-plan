package com.plantops.scenario;

import com.plantops.persistence.entity.OperationTransferTimeRuleEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 工序流转时间规则索引（产品 + 前工序 + 后工序）。 */
public final class OperationTransferTimeIndex {

    public record Rule(
            String productCode,
            String fromOperationName,
            String toOperationName,
            int transferMinutes,
            int minTransferMinutes) {
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
                .map(e -> new Rule(
                        e.productCode,
                        e.fromOperationName,
                        e.toOperationName,
                        e.transferMinutes,
                        e.minTransferMinutes))
                .toList();
        return new OperationTransferTimeIndex(rules);
    }

    public int transferMinutes(String productCode, String fromOperationName, String toOperationName) {
        Rule rule = lookup(productCode, fromOperationName, toOperationName);
        return rule != null ? Math.max(0, rule.transferMinutes()) : 0;
    }

    public int minTransferMinutes(String productCode, String fromOperationName, String toOperationName) {
        Rule rule = lookup(productCode, fromOperationName, toOperationName);
        return rule != null ? Math.max(0, rule.minTransferMinutes()) : 0;
    }

    public Rule lookup(String productCode, String fromOperationName, String toOperationName) {
        if (productCode == null || fromOperationName == null || toOperationName == null) {
            return null;
        }
        return byKey.get(key(productCode.trim(), fromOperationName.trim(), toOperationName.trim()));
    }

    private static String key(String productCode, String fromOperationName, String toOperationName) {
        return productCode + "|" + fromOperationName + "->" + toOperationName;
    }
}
