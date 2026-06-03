package com.plantops.solver.detailschedule;

import com.plantops.scenario.OperationLinkMode;
import com.plantops.scenario.OperationTransferTimeIndex;

/**
 * 工序衔接 Hard 约束：按生产规则计算违反分钟数（0 表示满足）。
 */
public final class OperationLinkConstraintSupport {

    private OperationLinkConstraintSupport() {
    }

    public static int violationMinutes(
            OperationAssignment pred,
            OperationAssignment succ,
            OperationTransferTimeIndex.ResolvedRule rule) {
        if (pred.getStartMinute() == null || succ.getStartMinute() == null) {
            return 0;
        }
        OperationLinkMode mode = rule != null ? rule.linkMode() : OperationLinkMode.STANDARD;
        int minGap = rule != null ? rule.minTransferMinutes() : 0;
        int maxGap = rule != null ? rule.maxTransferMinutes() : 0;
        int delayStart = rule != null ? rule.delayStartMinutes() : 0;

        int predStart = pred.getStartMinute();
        int predEnd = pred.getEndMinute();
        int succStart = succ.getStartMinute();
        int succEnd = succ.getEndMinute();

        return switch (mode) {
            case SIMULTANEOUS_START -> Math.abs(succStart - predStart);
            case DELAYED_START -> Math.max(0, predStart + delayStart - succStart);
            case SIMULTANEOUS_END -> Math.abs(succEnd - predEnd);
            case STANDARD -> standardViolation(predEnd, succStart, minGap, maxGap);
        };
    }

    private static int standardViolation(int predEnd, int succStart, int minGap, int maxGap) {
        int earliest = predEnd + Math.max(0, minGap);
        int tooEarly = Math.max(0, earliest - succStart);
        if (maxGap <= 0) {
            return tooEarly;
        }
        int latest = predEnd + maxGap;
        int tooLate = Math.max(0, succStart - latest);
        return Math.max(tooEarly, tooLate);
    }
}
