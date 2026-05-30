package com.plantops.solver.masterplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 主计划约束与 {@link com.plantops.scenario.MaterialFeasibilityService} 共用的 MRP 可行性判定。 */
public final class MaterialFeasibilityEvaluator {

    private static final int DEMAND_OFFSET_DAYS_PER_LEVEL = 3;

    private MaterialFeasibilityEvaluator() {
    }

    public static boolean isFeasible(
            String productCode,
            BigDecimal quantity,
            LocalDate productionDate,
            MaterialFeasibilityContext context) {
        if (productCode == null || productionDate == null || context == null) {
            return true;
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return checkMaterials(productCode, productCode, quantity, productionDate, context);
    }

    public static boolean isFeasible(
            String finishedProductCode,
            String productCode,
            BigDecimal quantity,
            LocalDate productionDate,
            MaterialFeasibilityContext context) {
        if (productCode == null || productionDate == null || context == null) {
            return true;
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        String finished = finishedProductCode != null && !finishedProductCode.isBlank()
                ? finishedProductCode
                : productCode;
        return checkMaterials(finished, productCode, quantity, productionDate, context);
    }

    private static boolean checkMaterials(
            String finishedProductCode,
            String productCode,
            BigDecimal quantity,
            LocalDate needByDate,
            MaterialFeasibilityContext context) {
        List<MaterialFeasibilityContext.ComponentNeed> components =
                context.componentsOfFinished(finishedProductCode, productCode);
        if (components.isEmpty()) {
            return true;
        }
        for (MaterialFeasibilityContext.ComponentNeed bom : components) {
            if (!bom.critical()) {
                continue;
            }
            BigDecimal need = bom.componentQty().multiply(quantity);
            String component = bom.componentProductCode();
            if (bom.manufactured()) {
                LocalDate subDate = needByDate.minusDays(DEMAND_OFFSET_DAYS_PER_LEVEL);
                if (!checkMaterials(finishedProductCode, component, need, subDate, context)) {
                    return false;
                }
            } else if (context.closingOn(component, needByDate).compareTo(need) < 0) {
                return false;
            }
        }
        return true;
    }
}
