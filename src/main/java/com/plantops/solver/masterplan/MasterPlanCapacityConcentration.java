package com.plantops.solver.masterplan;

/**
 * 产能集中目标：开线槽位惩罚与槽内剩余产能惩罚（供约束与单测复用）。
 */
public final class MasterPlanCapacityConcentration {

    private MasterPlanCapacityConcentration() {
    }

    /** 每占用一个时间槽的基础惩罚（与槽总产能成正比，促使减少开线次数）。 */
    public static int activeSlotPenalty(int slotCapacityMinutes, int objectiveWeight) {
        if (objectiveWeight <= 0 || slotCapacityMinutes <= 0) {
            return 0;
        }
        return slotCapacityMinutes * objectiveWeight;
    }

    /** 已占用槽位内剩余可用产能惩罚（促使用足产能）。 */
    public static int unusedCapacityPenalty(
            int slotCapacityMinutes,
            int fixedOverlayMinutes,
            int allocatedMinutes,
            int objectiveWeight) {
        if (objectiveWeight <= 0 || allocatedMinutes <= 0) {
            return 0;
        }
        int spare = Math.max(0, slotCapacityMinutes - Math.max(0, fixedOverlayMinutes) - allocatedMinutes);
        return spare * objectiveWeight;
    }
}
