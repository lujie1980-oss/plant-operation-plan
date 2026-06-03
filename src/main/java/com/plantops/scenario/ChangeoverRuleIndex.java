package com.plantops.scenario;

import com.plantops.masterdata.ProductResourceOperationNames;
import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.ProductResourceEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ChangeoverProductAttributeIndex attributes;
    private final Map<String, String> matrixOpByProductResource;
    private final Map<String, String> matrixOpByProductSeq;

    public ChangeoverRuleIndex(List<Rule> rules) {
        this(rules, ChangeoverProductAttributeIndex.empty(), Map.of(), Map.of());
    }

    public ChangeoverRuleIndex(
            List<Rule> rules,
            ChangeoverProductAttributeIndex attributes,
            Map<String, String> matrixOpByProductResource,
            Map<String, String> matrixOpByProductSeq) {
        this.rules = rules != null ? List.copyOf(rules) : List.of();
        this.attributes = attributes != null ? attributes : ChangeoverProductAttributeIndex.empty();
        this.matrixOpByProductResource = matrixOpByProductResource != null
                ? Map.copyOf(matrixOpByProductResource)
                : Map.of();
        this.matrixOpByProductSeq = matrixOpByProductSeq != null
                ? Map.copyOf(matrixOpByProductSeq)
                : Map.of();
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
        return withSnapshots(rules);
    }

    public static ChangeoverRuleIndex withSnapshots(List<Rule> rules) {
        Map<String, String> byResource = new HashMap<>();
        Map<String, String> bySeq = new HashMap<>();
        loadOperationNameMaps(byResource, bySeq);
        return new ChangeoverRuleIndex(
                rules,
                ChangeoverProductAttributeIndex.fromWorkspace(),
                byResource,
                bySeq);
    }

    private static void loadOperationNameMaps(Map<String, String> byResource, Map<String, String> bySeq) {
        for (ProductResourceEntity row : ProductResourceEntity.listInWorkspace()) {
            if (row.productCode == null || row.productCode.isBlank()) {
                continue;
            }
            String productCode = row.productCode.trim();
            String matrixOp = ProductResourceOperationNames.normalize(
                    row.operationName, row.resourceId, row.sequenceNo);
            if (matrixOp == null || matrixOp.isBlank()) {
                continue;
            }
            if (row.resourceId != null && !row.resourceId.isBlank()) {
                byResource.putIfAbsent(productCode + "|" + row.resourceId.trim(), matrixOp);
            }
            if (row.sequenceNo != null && row.sequenceNo > 0) {
                bySeq.putIfAbsent(productCode + "|" + row.sequenceNo, matrixOp);
            }
        }
    }

    public Set<String> knownOperationNames() {
        return rules.stream().map(Rule::operationName).collect(Collectors.toSet());
    }

    public String lookupMatrixOperationName(String productCode, String resourceId, int operationSeq) {
        if (productCode != null && !productCode.isBlank()) {
            String product = productCode.trim();
            if (resourceId != null && !resourceId.isBlank()) {
                String mapped = matrixOpByProductResource.get(product + "|" + resourceId.trim());
                if (mapped != null) {
                    return mapped;
                }
            }
            if (operationSeq > 0) {
                String mapped = matrixOpByProductSeq.get(product + "|" + operationSeq);
                if (mapped != null) {
                    return mapped;
                }
            }
        }
        return null;
    }

    public int computeMinutes(
            String routingOperationName,
            String resourceId,
            int operationSeq,
            String previousProductCode,
            String nextProductCode) {
        if (previousProductCode == null
                || nextProductCode == null
                || previousProductCode.isBlank()
                || nextProductCode.isBlank()) {
            return 0;
        }
        String op = ChangeoverOperationNameResolver.resolve(
                routingOperationName, resourceId, nextProductCode, operationSeq, this);
        if (op.isBlank()) {
            return 0;
        }
        return computeMinutesForMatrixOperation(op, previousProductCode, nextProductCode);
    }

    public int computeMinutes(String operationName, String previousProductCode, String nextProductCode) {
        return computeMinutes(operationName, null, -1, previousProductCode, nextProductCode);
    }

    private int computeMinutesForMatrixOperation(
            String matrixOperationName,
            String previousProductCode,
            String nextProductCode) {
        int total = 0;
        String op = matrixOperationName.trim();
        for (Rule rule : rules) {
            if (!op.equals(rule.operationName())) {
                continue;
            }
            String fromVal = attributes.resolve(previousProductCode, op, rule.attributeKey());
            String toVal = attributes.resolve(nextProductCode, op, rule.attributeKey());
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
