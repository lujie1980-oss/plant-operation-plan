package com.plantops.solver.masterplan;

import com.plantops.scenario.ChangeoverRuleIndex;

/**
 * 主计划槽内产品切换惩罚（同一 TimeSlot 内不同产品组合）。
 */
public final class MasterPlanSlotChangeover {

    /** 换型矩阵未命中时的名义切换成本（分钟），仍促使减少槽内产品种类。 */
    static final int FALLBACK_SWITCH_MINUTES = 30;

    private MasterPlanSlotChangeover() {
    }

    public static int switchPenaltyMinutes(
            OrderAllocation left,
            OrderAllocation right,
            ChangeoverRuleIndex changeoverRules) {
        if (left == null || right == null) {
            return 0;
        }
        String fromProduct = left.getProductCode();
        String toProduct = right.getProductCode();
        if (fromProduct == null || toProduct == null || fromProduct.equals(toProduct)) {
            return 0;
        }
        String operationName = resolveOperationName(left, right);
        if (operationName == null || changeoverRules == null) {
            return FALLBACK_SWITCH_MINUTES;
        }
        int minutes = changeoverRules.computeMinutes(operationName, fromProduct, toProduct);
        return minutes > 0 ? minutes : FALLBACK_SWITCH_MINUTES;
    }

    static String resolveOperationName(OrderAllocation left, OrderAllocation right) {
        String opA = left.getOperationName();
        String opB = right.getOperationName();
        if (opA != null && !opA.isBlank()) {
            if (opB == null || opB.isBlank() || opA.equals(opB)) {
                return opA.trim();
            }
        }
        if (opB != null && !opB.isBlank()) {
            return opB.trim();
        }
        return null;
    }
}
