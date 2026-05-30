package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.ScoreExplanation;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 选优层得分分解入口：恢复已持久化解 + {@code SolutionManager.explain()}。
 */
@ApplicationScoped
public class PlanningScoreExplainService {

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    @Inject
    MasterPlanSolutionRestorer masterPlanRestorer;

    @Inject
    DetailScheduleSolutionRestorer detailScheduleRestorer;

    @Inject
    PlanningScoreExplainer scoreExplainer;

    public PlanningScoreExplanationDto explainMasterPlan(String planVersionId) {
        MasterPlanSchedule schedule = masterPlanRestorer.restore(planVersionId);
        SolutionManager<MasterPlanSchedule, HardSoftScore> manager =
                solverRuntimeFactory.createMasterPlanSolutionManager();
        ScoreExplanation<MasterPlanSchedule, HardSoftScore> explanation = manager.explain(schedule);
        return scoreExplainer.explainMasterPlan(planVersionId, explanation);
    }

    public PlanningScoreExplanationDto explainDetailSchedule(String detailScheduleVersionId, String masterPlanVersionId) {
        DetailSchedule schedule = detailScheduleRestorer.restore(detailScheduleVersionId, masterPlanVersionId);
        SolutionManager<DetailSchedule, HardSoftScore> manager =
                solverRuntimeFactory.createDetailScheduleSolutionManager();
        ScoreExplanation<DetailSchedule, HardSoftScore> explanation = manager.explain(schedule);
        return scoreExplainer.explainDetailSchedule(
                detailScheduleVersionId, masterPlanVersionId, explanation);
    }
}
