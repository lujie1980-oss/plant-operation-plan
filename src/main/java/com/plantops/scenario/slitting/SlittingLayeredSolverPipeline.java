package com.plantops.scenario.slitting;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.SlittingConstructionHeuristic;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class SlittingLayeredSolverPipeline {

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    @Inject
    SlittingProblemMapper problemMapper;

    public SlittingLayeredResult solve(SlittingPlanningContext ctx) throws ExecutionException, InterruptedException {
        SlittingNestSolution phase1Problem = problemMapper.toPhase1Solution(ctx);
        SlittingConstructionHeuristic.seedFFD(phase1Problem);
        SlittingNestSolution phase1Solved = solve(phase1Problem);

        List<RollNode> intermediates = problemMapper.materializeIntermediates(phase1Solved);
        SlittingNestSolution phase2Problem = problemMapper.toPhase2Solution(ctx.masterRolls(), intermediates);
        SlittingConstructionHeuristic.seedFFD(phase2Problem);
        SlittingNestSolution phase2Solved = solve(phase2Problem);

        return SlittingLayeredResult.merge(phase1Solved, phase2Solved);
    }

    private SlittingNestSolution solve(SlittingNestSolution problem) throws ExecutionException, InterruptedException {
        String jobId = "SLIT-" + UUID.randomUUID();
        try (SolverManager<SlittingNestSolution> solver = solverRuntimeFactory.createSlittingNestSolver()) {
            return solver.solve(jobId, problem).getFinalBestSolution();
        }
    }
}
