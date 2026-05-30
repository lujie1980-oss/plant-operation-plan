package com.plantops.solver.masterplan;

/**
 * 主计划求解配置（单例问题事实，供约束流读取）。
 */
public class MasterPlanSettings {

    private MasterPlanCapacityStrategy capacityStrategy = MasterPlanCapacityStrategy.UNCONSTRAINED;

    public MasterPlanSettings() {
    }

    public MasterPlanSettings(MasterPlanCapacityStrategy capacityStrategy) {
        this.capacityStrategy = capacityStrategy != null ? capacityStrategy : MasterPlanCapacityStrategy.UNCONSTRAINED;
    }

    public MasterPlanCapacityStrategy getCapacityStrategy() {
        return capacityStrategy;
    }

    public void setCapacityStrategy(MasterPlanCapacityStrategy capacityStrategy) {
        this.capacityStrategy = capacityStrategy != null ? capacityStrategy : MasterPlanCapacityStrategy.UNCONSTRAINED;
    }

    public boolean isCapacityConstrained() {
        return capacityStrategy.isCapacityConstrained();
    }
}
