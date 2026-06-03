package com.plantops.scenario;

import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

/**
 * 按工序 + 工艺 BOM 属性前后值计算换型时间（分钟）。
 * 规则与 Excel「换型时间.xlsx / KTPrefixDuration」一致。
 */
@ApplicationScoped
public class ChangeoverDurationService {

    /**
     * 按换型矩阵匹配换型分钟数：从工艺 BOM 解析前后任务的属性值再与规则比对。
     * 料号相同但线材/分支等属性不同时仍可命中（如 *→* 规则）。
     */
    public int computeMinutes(String operationName, String previousProductCode, String nextProductCode) {
        if (previousProductCode == null
                || nextProductCode == null
                || previousProductCode.isBlank()
                || nextProductCode.isBlank()) {
            return 0;
        }
        if (operationName == null || operationName.isBlank()) {
            return 0;
        }
        List<ChangeoverMatrixEntity> rules = ChangeoverMatrixEntity.findByOperation(operationName.trim());
        if (rules.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChangeoverMatrixEntity rule : rules) {
            String fromVal = ChangeoverProductAttributeResolver.resolve(
                    previousProductCode, operationName, rule.attributeKey);
            String toVal = ChangeoverProductAttributeResolver.resolve(
                    nextProductCode, operationName, rule.attributeKey);
            if (ruleMatches(rule, fromVal, toVal)) {
                total += Math.max(0, rule.setupMinutes);
            }
        }
        return total;
    }

    /** @deprecated 使用 {@link ChangeoverProductAttributeResolver#resolve(String, String, String)} */
    @Deprecated
    public String resolveAttributeValue(String productCode, String operationName, String attributeKey) {
        return ChangeoverProductAttributeResolver.resolve(productCode, operationName, attributeKey);
    }

    static boolean ruleMatches(ChangeoverMatrixEntity rule, String fromVal, String toVal) {
        String fromPattern = ChangeoverAttributeKey.normalizeValue(rule.fromAttributeValue);
        String toPattern = ChangeoverAttributeKey.normalizeValue(rule.toAttributeValue);
        String fromActual = ChangeoverAttributeKey.normalizeValue(fromVal);
        String toActual = ChangeoverAttributeKey.normalizeValue(toVal);

        boolean fromOk = ChangeoverAttributeKey.isWildcard(fromPattern) || fromPattern.equals(fromActual);
        boolean toOk = ChangeoverAttributeKey.isWildcard(toPattern) || toPattern.equals(toActual);
        if (!fromOk || !toOk) {
            return false;
        }
        if (ChangeoverAttributeKey.isWildcard(fromPattern) && ChangeoverAttributeKey.isWildcard(toPattern)) {
            return !Objects.equals(fromActual, toActual);
        }
        return true;
    }
}
