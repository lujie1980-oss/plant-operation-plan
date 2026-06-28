package com.plantops.config;

import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanSettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 从系统参数构建 {@link MasterPlanSettings}（主计划 Timefold 问题事实）。 */
@ApplicationScoped
public class MasterPlanPlanningSettingsFactory {

    public static final String PARAM_MATERIAL_CONSTRAINT_ENABLED = "master_plan_material_constraint_enabled";

    @Inject
    ParameterRegistry parameters;

    public MasterPlanSettings create(MasterPlanCapacityStrategy capacityStrategy) {
        return new MasterPlanSettings(
                capacityStrategy,
                parameters.getBoolean(PARAM_MATERIAL_CONSTRAINT_ENABLED, false));
    }
}
