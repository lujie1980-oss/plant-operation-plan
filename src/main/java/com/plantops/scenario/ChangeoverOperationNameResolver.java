package com.plantops.scenario;

import com.plantops.masterdata.ProductResourceOperationNames;

import java.util.Set;

/**
 * 将工艺路线上的工序名（常为「工序 N」）解析为换型矩阵中的工序名（裁线/半成品/成品/标签/气密）。
 */
public final class ChangeoverOperationNameResolver {

    private ChangeoverOperationNameResolver() {
    }

    public static String resolve(
            String routingOperationName,
            String resourceId,
            String productCode,
            int operationSeq,
            ChangeoverRuleIndex rules) {
        Set<String> ruleOps = operationNamesFrom(rules);
        if (routingOperationName != null && !routingOperationName.isBlank()) {
            String trimmed = routingOperationName.trim();
            if (ruleOps.contains(trimmed)) {
                return trimmed;
            }
        }
        if (rules != null && productCode != null && !productCode.isBlank()) {
            String fromSnapshot = rules.lookupMatrixOperationName(productCode, resourceId, operationSeq);
            if (fromSnapshot != null && ruleOps.contains(fromSnapshot)) {
                return fromSnapshot;
            }
        }
        String normalized = ProductResourceOperationNames.normalize(
                routingOperationName,
                resourceId,
                operationSeq > 0 ? operationSeq : null);
        if (normalized != null && !normalized.isBlank() && ruleOps.contains(normalized)) {
            return normalized;
        }
        return routingOperationName != null ? routingOperationName.trim() : "";
    }

    private static Set<String> operationNamesFrom(ChangeoverRuleIndex rules) {
        if (rules == null) {
            return Set.of();
        }
        return rules.knownOperationNames();
    }
}
