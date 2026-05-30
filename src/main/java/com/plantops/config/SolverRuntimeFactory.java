package com.plantops.config;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleConstraintProvider;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.masterplan.MasterPlanConstraintProvider;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 每次求解前按当前系统参数创建 SolverManager，避免启动时固化求解时限。
 */
@ApplicationScoped
public class SolverRuntimeFactory {

    @Inject
    ParameterRegistry parameters;

    public SolverManager<MasterPlanSchedule, String> createMasterPlanSolver() {
        return SolverManager.create(SolverFactory.create(masterPlanSolverConfig(true)));
    }

    public SolverManager<DetailSchedule, String> createDetailScheduleSolver() {
        return SolverManager.create(SolverFactory.create(detailScheduleSolverConfig(true)));
    }

    public SolutionManager<MasterPlanSchedule, HardSoftScore> createMasterPlanSolutionManager() {
        return SolutionManager.create(SolverFactory.create(masterPlanSolverConfig(false)));
    }

    public SolutionManager<DetailSchedule, HardSoftScore> createDetailScheduleSolutionManager() {
        return SolutionManager.create(SolverFactory.create(detailScheduleSolverConfig(false)));
    }

    private SolverConfig masterPlanSolverConfig(boolean withTermination) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(MasterPlanSchedule.class)
                .withEntityClasses(OrderAllocation.class)
                .withConstraintProviderClass(MasterPlanConstraintProvider.class);
        if (withTermination) {
            long seconds = Math.max(1L, parameters.getInt("master_plan_solver_seconds", 30));
            config.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
        }
        return config;
    }

    private SolverConfig detailScheduleSolverConfig(boolean withTermination) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(DetailSchedule.class)
                .withEntityClasses(OperationAssignment.class)
                .withConstraintProviderClass(DetailScheduleConstraintProvider.class);
        if (withTermination) {
            long seconds = Math.max(1L, parameters.getInt("detail_schedule_solver_seconds", 30));
            config.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
        }
        return config;
    }
}
