package com.plantops.config;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.DetailScheduleConstraintProvider;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import com.plantops.solver.masterplan.MasterPlanConstraintProvider;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.slitting.NestAssignment;
import com.plantops.solver.slitting.SlittingConstraintProvider;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 每次求解前按当前系统参数创建 SolverManager，避免启动时固化求解时限。
 */
@ApplicationScoped
public class SolverRuntimeFactory {

    @Inject
    ParameterRegistry parameters;

    public SolverManager<MasterPlanSchedule> createMasterPlanSolver() {
        return SolverManager.create(SolverFactory.create(masterPlanSolverConfig(true)));
    }

    public SolverManager<DetailSchedule> createDetailScheduleSolver() {
        return SolverManager.create(SolverFactory.create(detailScheduleSolverConfig(true)));
    }

    public SolutionManager<MasterPlanSchedule, HardSoftScore> createMasterPlanSolutionManager() {
        return SolutionManager.create(SolverFactory.create(masterPlanSolverConfig(false)));
    }

    public SolutionManager<DetailSchedule, HardSoftScore> createDetailScheduleSolutionManager() {
        return SolutionManager.create(SolverFactory.create(detailScheduleSolverConfig(false)));
    }

    public SolverManager<SlittingNestSolution> createSlittingNestSolver() {
        return SolverManager.create(SolverFactory.create(slittingNestSolverConfig(true)));
    }

    public SolverManager<SlittingNestSolution> createSlittingNestSessionSolver() {
        return SolverManager.create(SolverFactory.create(slittingNestSessionSolverConfig()));
    }

    public SolutionManager<SlittingNestSolution, HardSoftScore> createSlittingNestSolutionManager() {
        return SolutionManager.create(SolverFactory.create(slittingNestSolverConfig(false)));
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
                .withEntityClasses(ScheduleLine.class, OperationAssignment.class)
                .withConstraintProviderClass(DetailScheduleConstraintProvider.class);
        if (withTermination) {
            long seconds = Math.max(1L, parameters.getInt("detail_schedule_solver_seconds", 30));
            config.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
        }
        return config;
    }

    private SolverConfig slittingNestSolverConfig(boolean withTermination) {
        SolverConfig config = new SolverConfig()
                .withSolutionClass(SlittingNestSolution.class)
                .withEntityClasses(NestAssignment.class)
                .withConstraintProviderClass(SlittingConstraintProvider.class);
        if (withTermination) {
            long seconds = Math.max(1L, parameters.getInt("slitting_solver_seconds", 30));
            config.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
        }
        return config;
    }

    private SolverConfig slittingNestSessionSolverConfig() {
        long seconds = Math.max(1L, parameters.getInt("slitting_session_solver_seconds", 10));
        return new SolverConfig()
                .withSolutionClass(SlittingNestSolution.class)
                .withEntityClasses(NestAssignment.class)
                .withConstraintProviderClass(SlittingConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(seconds));
    }
}
