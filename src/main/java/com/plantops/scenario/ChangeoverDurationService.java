package com.plantops.scenario;

import com.plantops.persistence.entity.ChangeoverMatrixEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;

/**
 * 按工序 + 属性前后值计算换型时间（分钟）。
 * 规则与 Excel「换型时间.xlsx / KTPrefixDuration」一致。
 */
@ApplicationScoped
public class ChangeoverDurationService {

    public int computeMinutes(String operationName, String previousProductCode, String nextProductCode) {
        if (previousProductCode == null
                || nextProductCode == null
                || previousProductCode.isBlank()
                || nextProductCode.isBlank()
                || previousProductCode.equals(nextProductCode)) {
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
            String fromVal = resolveAttributeValue(previousProductCode, operationName, rule.attributeKey);
            String toVal = resolveAttributeValue(nextProductCode, operationName, rule.attributeKey);
            if (ruleMatches(rule, fromVal, toVal)) {
                total += Math.max(0, rule.setupMinutes);
            }
        }
        return total;
    }

    public String resolveAttributeValue(String productCode, String operationName, String attributeKey) {
        String key = ChangeoverAttributeKey.normalizeCode(attributeKey);
        if (ChangeoverAttributeKey.PRODUCT_CODE.code().equals(key)) {
            return ChangeoverAttributeKey.normalizeValue(productCode);
        }
        ProductResourceEntity row = ProductResourceEntity.findByProductAndOperation(productCode, operationName);
        if (row == null) {
            row = ProductResourceEntity.findFirstByProduct(productCode);
        }
        if (row == null) {
            return ChangeoverAttributeKey.wildcard();
        }
        return switch (key) {
            case "wireMaterial" -> ChangeoverAttributeKey.normalizeValue(row.wireMaterial);
            case "keyMaterial" -> ChangeoverAttributeKey.normalizeValue(row.keyMaterial);
            case "totalBranch" -> ChangeoverAttributeKey.normalizeValue(row.totalBranch);
            default -> ChangeoverAttributeKey.wildcard();
        };
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
