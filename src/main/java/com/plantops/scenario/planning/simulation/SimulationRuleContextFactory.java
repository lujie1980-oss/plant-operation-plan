package com.plantops.scenario.planning.simulation;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleProblemFacts;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

public final class SimulationRuleContextFactory {

    private SimulationRuleContextFactory() {
    }

    public static SimulationRuleContext from(
            DetailSchedule schedule,
            SimulationMode mode,
            Set<String> seedOperationIds) {
        DetailScheduleProblemFacts facts = schedule != null ? schedule.getProblemFacts() : null;
        LocalDate anchor = facts != null && facts.planningAnchorDate() != null
                ? facts.planningAnchorDate()
                : LocalDate.now();
        return new SimulationRuleContext(
                schedule,
                facts,
                null,
                Map.of(),
                mode,
                seedOperationIds != null ? seedOperationIds : Set.of(),
                anchor);
    }
}
