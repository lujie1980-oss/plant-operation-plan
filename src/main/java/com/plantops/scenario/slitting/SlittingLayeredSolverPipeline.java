package com.plantops.scenario.slitting;

import com.plantops.solver.slitting.RollNode;
import com.plantops.solver.slitting.SlittingConstructionHeuristic;
import com.plantops.solver.slitting.SlittingNestSolution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class SlittingLayeredSolverPipeline {

    @Inject
    SlittingLoggedSolver loggedSolver;

    @Inject
    SlittingSolverRunService runService;

    @Inject
    SlittingProblemMapper problemMapper;

    public SlittingLayeredResult solve(SlittingPlanningContext ctx) throws ExecutionException, InterruptedException {
        return solve(ctx, null);
    }

    public SlittingLayeredResult solve(SlittingPlanningContext ctx, String existingRunId)
            throws ExecutionException, InterruptedException {
        long started = System.currentTimeMillis();
        String runId = existingRunId;
        if (runId == null) {
            runId = runService.startRun(
                    SlittingSolverRunService.TYPE_PLAN_SOLVE,
                    ctx.planVersionId(),
                    null,
                    null,
                    "整方案分切求解开始，方案 " + ctx.planVersionId());
        }

        try {
            SlittingNestSolution phase1Problem = problemMapper.toPhase1Solution(ctx);
            SlittingConstructionHeuristic.seedFFD(phase1Problem);
            runService.appendLog(runId, "INFO", "阶段一：母卷 → 中间卷 启发式初排完成");
            SlittingNestSolution phase1Solved = loggedSolver.solvePlanLayer(runId, "阶段一 Timefold", phase1Problem);

            List<RollNode> intermediates = problemMapper.materializeIntermediates(phase1Solved);
            SlittingNestSolution phase2Problem = problemMapper.toPhase2Solution(ctx.masterRolls(), intermediates);
            SlittingConstructionHeuristic.seedFFD(phase2Problem);
            runService.appendLog(runId, "INFO", "阶段二：中间卷 → 子订单 启发式初排完成");
            SlittingNestSolution phase2Solved = loggedSolver.solvePlanLayer(runId, "阶段二 Timefold", phase2Problem);

            SlittingLayeredResult result = SlittingLayeredResult.merge(phase1Solved, phase2Solved);
            long duration = System.currentTimeMillis() - started;
            if (existingRunId == null) {
                String score = result.score() != null ? result.score().toString() : null;
                runService.finishSuccess(
                        runId,
                        duration,
                        score,
                        "两阶段求解完成，得分 " + (score != null ? score : "—"));
            }
            return result;
        } catch (RuntimeException | ExecutionException | InterruptedException ex) {
            if (existingRunId == null) {
                runService.finishFailed(runId, System.currentTimeMillis() - started, ex.getMessage());
            }
            throw ex;
        }
    }
}
