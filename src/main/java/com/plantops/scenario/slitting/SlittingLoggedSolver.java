package com.plantops.scenario.slitting;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@ApplicationScoped
public class SlittingLoggedSolver {

    @Inject
    SlittingSolverRunService runService;

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    public SlittingNestSolution solvePlanLayer(
            String runId,
            String phaseLabel,
            SlittingNestSolution problem) throws ExecutionException, InterruptedException {
        return solve(runId, phaseLabel, problem, solverRuntimeFactory::createSlittingNestSolver);
    }

    public SlittingNestSolution solveSessionLayer(
            String runId,
            String phaseLabel,
            SlittingNestSolution problem) throws ExecutionException, InterruptedException {
        return solve(runId, phaseLabel, problem, solverRuntimeFactory::createSlittingNestSessionSolver);
    }

    private SlittingNestSolution solve(
            String runId,
            String phaseLabel,
            SlittingNestSolution problem,
            Supplier<SolverManager<SlittingNestSolution>> solverSupplier)
            throws ExecutionException, InterruptedException {
        int entities = problem.getAssignments() != null ? problem.getAssignments().size() : 0;
        runService.appendLog(
                runId,
                "INFO",
                phaseLabel + "：待优化分配 " + entities + " 条，容器 "
                        + (problem.getContainers() != null ? problem.getContainers().size() : 0) + " 个");
        String jobId = "SLIT-" + UUID.randomUUID();
        try (SlittingTimefoldLogCapture capture = SlittingTimefoldLogCapture.attach(runId, runService);
                SolverManager<SlittingNestSolution> solver = solverSupplier.get()) {
            SlittingNestSolution solved = solver.solve(jobId, problem).getFinalBestSolution();
            String score = solved.getScore() != null ? solved.getScore().toString() : "—";
            runService.appendLog(runId, "INFO", phaseLabel + " 完成，得分 " + score);
            return solved;
        }
    }
}
