package com.plantops.scenario.planning.optimizer;

import com.plantops.scenario.planning.MasterPlanProblemMapper;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** 从 {@link PlanningProblem} 解析 Timefold {@link MasterPlanSchedule} 输入。 */
@ApplicationScoped
public class PlanningProblemScheduleResolver {

    @Inject
    MasterPlanProblemMapper problemMapper;

    public MasterPlanSchedule resolve(PlanningProblem problem) {
        if (problem == null) {
            throw new IllegalArgumentException("PlanningProblem is required");
        }
        if (problem.ontologySchedule() != null) {
            return problem.ontologySchedule();
        }
        if (problem.context() != null) {
            return problemMapper.toSchedule(problem.context());
        }
        throw new IllegalArgumentException("PlanningProblem requires context or ontologySchedule");
    }
}
