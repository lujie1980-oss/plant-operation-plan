package com.plantops.scenario.planning;

/** 主计划工序需求（工时分钟）缩放，不影响 MRP 数量与详细排程。 */
public final class MasterPlanDemandScaler {

    /** 参数 {@code master_plan_demand_scale} 未配置时的默认值：需求降为 1/100。 */
    public static final double DEFAULT_SCALE = 0.01;

    private MasterPlanDemandScaler() {
    }

    public static int scaleMinutes(int rawMinutes, double scale) {
        if (rawMinutes <= 0) {
            return 1;
        }
        if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
            return Math.max(1, rawMinutes);
        }
        if (Math.abs(scale - 1.0) < 1e-9) {
            return Math.max(1, rawMinutes);
        }
        long scaled = Math.round(rawMinutes * scale);
        return (int) Math.max(1, scaled);
    }
}
