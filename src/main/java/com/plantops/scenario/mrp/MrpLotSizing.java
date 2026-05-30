package com.plantops.scenario.mrp;

import com.plantops.persistence.entity.BomComponentEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** BOM 父项批量取整：同父项多行规则冲突时取 max。 */
public final class MrpLotSizing {

    private MrpLotSizing() {
    }

    public record LotRule(BigDecimal lotSize, BigDecimal lotSizeMultiple) {
    }

    public static LotRule lotRuleForProduct(String parentProductCode, List<BomComponentEntity> allBom) {
        BigDecimal maxLot = null;
        BigDecimal maxMultiple = null;
        for (BomComponentEntity row : allBom) {
            if (row == null || !parentProductCode.equals(row.parentProductCode)) {
                continue;
            }
            if (row.lotSize != null) {
                maxLot = maxLot == null ? row.lotSize : maxLot.max(row.lotSize);
            }
            if (row.lotSizeMultiple != null) {
                maxMultiple = maxMultiple == null ? row.lotSizeMultiple : maxMultiple.max(row.lotSizeMultiple);
            }
        }
        return new LotRule(maxLot, maxMultiple);
    }

    public static BigDecimal apply(BigDecimal netQty, LotRule rule) {
        if (netQty == null || netQty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (rule == null || (rule.lotSize() == null && rule.lotSizeMultiple() == null)) {
            return netQty;
        }
        BigDecimal multiple = rule.lotSizeMultiple() != null
                && rule.lotSizeMultiple().compareTo(BigDecimal.ZERO) > 0
                ? rule.lotSizeMultiple()
                : BigDecimal.ONE;
        BigDecimal rounded = netQty
                .divide(multiple, 0, RoundingMode.CEILING)
                .multiply(multiple);
        if (rule.lotSize() != null && rule.lotSize().compareTo(BigDecimal.ZERO) > 0
                && rounded.compareTo(rule.lotSize()) < 0) {
            rounded = rule.lotSize();
        }
        return rounded;
    }
}
