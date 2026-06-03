package com.plantops.scenario.batch;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.RuleScopeHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** 按排程齐套规则（S05 关键件）计算工单可齐套生产量。 */
public final class BatchKittingQuantityCalculator {

    private BatchKittingQuantityCalculator() {
    }

    /**
     * 在可用库存池下，最多可生产的数量（不超过 {@code capQuantity}）。
     * 无关键子件时返回 capQuantity。
     */
    public static BigDecimal maxKittingQuantity(
            WorkOrderEntity wo,
            BigDecimal capQuantity,
            Map<String, BigDecimal> availablePool,
            RuleScopeHelper ruleScopeHelper) {
        if (wo == null || capQuantity == null || capQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (availablePool == null) {
            return BigDecimal.ZERO;
        }
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        List<BomComponentEntity> criticalLines = BomComponentEntity.findChildren(finished, wo.productCode).stream()
                .filter(ruleScopeHelper::criticalForDetailSchedule)
                .toList();
        if (criticalLines.isEmpty()) {
            return capQuantity.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal maxUnits = capQuantity;
        for (BomComponentEntity bom : criticalLines) {
            if (bom.componentQty == null || bom.componentQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal avail = availablePool.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            BigDecimal units = avail.divide(bom.componentQty, 0, RoundingMode.FLOOR);
            if (units.compareTo(maxUnits) < 0) {
                maxUnits = units;
            }
        }
        return maxUnits.max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    /** 从库存池扣减生产 {@code runQuantity} 所需关键件（与 S05 齐套消耗一致）。 */
    public static void consumeForQuantity(
            WorkOrderEntity wo,
            BigDecimal runQuantity,
            Map<String, BigDecimal> availablePool,
            RuleScopeHelper ruleScopeHelper) {
        if (wo == null || availablePool == null || runQuantity == null || runQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForDetailSchedule(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(runQuantity);
            String component = bom.componentProductCode;
            availablePool.put(
                    component,
                    availablePool.getOrDefault(component, BigDecimal.ZERO).subtract(need));
        }
    }

    public static boolean canKitQuantity(
            WorkOrderEntity wo,
            BigDecimal runQuantity,
            Map<String, BigDecimal> availablePool,
            RuleScopeHelper ruleScopeHelper) {
        if (wo == null || availablePool == null || runQuantity == null || runQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        String finished = BomComponentEntity.resolveFinishedProduct(wo);
        for (BomComponentEntity bom : BomComponentEntity.findChildren(finished, wo.productCode)) {
            if (!ruleScopeHelper.criticalForDetailSchedule(bom)) {
                continue;
            }
            BigDecimal need = bom.componentQty.multiply(runQuantity);
            BigDecimal avail = availablePool.getOrDefault(bom.componentProductCode, BigDecimal.ZERO);
            if (avail.compareTo(need) < 0) {
                return false;
            }
        }
        return true;
    }
}
