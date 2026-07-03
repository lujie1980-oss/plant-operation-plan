package com.plantops.scenario.planning.optimizer;

import com.plantops.scenario.planning.MasterPlanPlanningContext;
import com.plantops.solver.masterplan.MasterPlanSchedule;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 求解器无关问题描述。Sandbox 以 {@link MasterPlanPlanningContext} 桥接；
 * Session 直驱以 {@link MasterPlanSchedule}（由 {@code OntologyGraph} 投影）提交。
 */
public record PlanningProblem(
        MasterPlanPlanningContext context,
        String scopeLabel,
        Set<String> scopedSupplyOrderIds,
        MasterPlanSchedule ontologySchedule) {

    public PlanningProblem(MasterPlanPlanningContext context, String scopeLabel, Set<String> scopedSupplyOrderIds) {
        this(context, scopeLabel, scopedSupplyOrderIds, null);
    }

    public PlanningProblem {
        scopedSupplyOrderIds = scopedSupplyOrderIds == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(scopedSupplyOrderIds));
    }

    public static PlanningProblem forOntologySchedule(MasterPlanSchedule schedule, String scopeLabel) {
        return new PlanningProblem(null, scopeLabel, Set.of(), schedule);
    }

    public static PlanningProblem forContext(
            MasterPlanPlanningContext context,
            String scopeLabel,
            Set<String> scopedSupplyOrderIds) {
        return new PlanningProblem(context, scopeLabel, scopedSupplyOrderIds, null);
    }
}
