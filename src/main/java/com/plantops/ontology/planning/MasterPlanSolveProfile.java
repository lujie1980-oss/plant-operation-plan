package com.plantops.ontology.planning;

import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;

import java.time.LocalDate;

/** Session / 直驱求解策略快照（与 {@link com.plantops.scenario.MasterPlanStrategyConfigService.ResolvedStrategy} 对齐）。 */
public record MasterPlanSolveProfile(
        LocalDate planningStart,
        MasterPlanCapacityStrategy capacityStrategy,
        MasterPlanObjectiveSettings objectiveSettings,
        MasterPlanCapacityOverlay capacityOverlay,
        String strategyId) {

    public MasterPlanSolveProfile {
        if (planningStart == null) {
            planningStart = LocalDate.now();
        }
        if (capacityStrategy == null) {
            capacityStrategy = MasterPlanCapacityStrategy.UNCONSTRAINED;
        }
        if (objectiveSettings == null) {
            objectiveSettings = new MasterPlanObjectiveSettings();
        }
        if (capacityOverlay == null) {
            capacityOverlay = MasterPlanCapacityOverlay.empty();
        }
    }

    public static MasterPlanSolveProfile defaults(LocalDate planningStart) {
        return new MasterPlanSolveProfile(
                planningStart,
                MasterPlanCapacityStrategy.UNCONSTRAINED,
                new MasterPlanObjectiveSettings(),
                MasterPlanCapacityOverlay.empty(),
                null);
    }
}
