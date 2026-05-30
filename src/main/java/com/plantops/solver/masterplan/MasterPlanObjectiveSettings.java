package com.plantops.solver.masterplan;

import java.util.Map;

/**
 * 主计划软目标权重（求解问题事实，供 {@link MasterPlanConstraintProvider} 读取）。
 */
public class MasterPlanObjectiveSettings {

    private final Map<String, Integer> weightsById;

    public MasterPlanObjectiveSettings() {
        this(MasterPlanObjectiveCatalog.defaults());
    }

    public MasterPlanObjectiveSettings(Map<String, Integer> weightsById) {
        this.weightsById = weightsById != null ? Map.copyOf(weightsById) : MasterPlanObjectiveCatalog.defaults();
    }

    /** 权重 &gt; 0 视为启用该软目标 */
    public int weight(String objectiveId) {
        if (objectiveId == null) {
            return 0;
        }
        return weightsById.getOrDefault(objectiveId, 0);
    }

    public boolean isEnabled(String objectiveId) {
        return weight(objectiveId) > 0;
    }
}
