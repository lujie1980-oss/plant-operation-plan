package com.plantops.solver.masterplan;

/**
 * 主计划求解配置（单例问题事实，供约束流读取）。
 */
public class MasterPlanSettings {

    private MasterPlanCapacityStrategy capacityStrategy = MasterPlanCapacityStrategy.UNCONSTRAINED;
    private boolean materialConstraintEnabled;

    public MasterPlanSettings() {
    }

    public MasterPlanSettings(MasterPlanCapacityStrategy capacityStrategy) {
        this(capacityStrategy, false);
    }

    public MasterPlanSettings(MasterPlanCapacityStrategy capacityStrategy, boolean materialConstraintEnabled) {
        this.capacityStrategy = capacityStrategy != null ? capacityStrategy : MasterPlanCapacityStrategy.UNCONSTRAINED;
        this.materialConstraintEnabled = materialConstraintEnabled;
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

    /** 为 true 时启用「排产日物料可行」硬约束；为 false 时求解不因缺料扣分。 */
    public boolean isMaterialConstraintEnabled() {
        return materialConstraintEnabled;
    }

    public void setMaterialConstraintEnabled(boolean materialConstraintEnabled) {
        this.materialConstraintEnabled = materialConstraintEnabled;
    }
}
