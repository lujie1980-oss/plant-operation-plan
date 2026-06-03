package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 选优层得分分解入口：恢复已持久化解 + {@code SolutionManager.analyze()}（Timefold 2.0）。
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
        return scoreExplainer.explainMasterPlan(planVersionId, manager.analyze(schedule));
    }

    public PlanningScoreExplanationDto explainDetailSchedule(String detailScheduleVersionId, String masterPlanVersionId) {
        DetailSchedule schedule = detailScheduleRestorer.restore(detailScheduleVersionId, masterPlanVersionId);
        SolutionManager<DetailSchedule, HardSoftScore> manager =
                solverRuntimeFactory.createDetailScheduleSolutionManager();
        return scoreExplainer.explainDetailSchedule(
                detailScheduleVersionId, masterPlanVersionId, manager.analyze(schedule));
    }
}
