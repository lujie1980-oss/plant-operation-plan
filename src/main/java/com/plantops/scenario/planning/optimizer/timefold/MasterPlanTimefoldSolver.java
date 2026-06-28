package com.plantops.scenario.planning.optimizer.timefold;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** 主计划 Timefold 求解（仅 {@code planning_optimizer_engine=timefold} 时使用）。 */
@ApplicationScoped
public class MasterPlanTimefoldSolver {

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    public MasterPlanSchedule solve(MasterPlanSchedule problem)
            throws ExecutionException, InterruptedException {
        String jobId = "MP-SOLVE-" + UUID.randomUUID();
        try (SolverManager<MasterPlanSchedule> solver = solverRuntimeFactory.createMasterPlanSolver()) {
            return solver.solve(jobId, problem).getFinalBestSolution();
        }
    }
}
