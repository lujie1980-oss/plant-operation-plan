package com.plantops.ontology.supply;

import com.plantops.persistence.entity.OperationPostProcessingRuleEntity;

/** 末道工序后处理规则（秒）。 */
public final class OperationPostProcessingResolver {

    private OperationPostProcessingResolver() {
    }

    public static long postprocessingSeconds(String productCode, String operationName) {
        if (productCode == null || productCode.isBlank()) {
            return 0;
        }
        if (operationName != null && !operationName.isBlank()) {
            OperationPostProcessingRuleEntity exact =
                    OperationPostProcessingRuleEntity.findEntry(productCode, operationName);
            if (exact != null) {
                return Math.max(0, (long) exact.postProcessingMinutes * 60);
            }
        }
        OperationPostProcessingRuleEntity wildcard =
                OperationPostProcessingRuleEntity.findEntry(productCode, "*");
        if (wildcard != null) {
            return Math.max(0, (long) wildcard.postProcessingMinutes * 60);
        }
        return 0;
    }
}
