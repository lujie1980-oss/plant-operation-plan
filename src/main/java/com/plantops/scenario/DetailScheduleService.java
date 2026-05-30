package com.plantops.scenario;



import ai.timefold.solver.core.api.solver.SolverManager;

import com.plantops.api.dto.DetailScheduleOperationDto;

import com.plantops.api.dto.DetailScheduleResultDto;

import com.plantops.api.dto.MasterPlanRefreshResultDto;

import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;

import com.plantops.config.SolverRuntimeFactory;

import com.plantops.persistence.entity.DetailScheduleOperationEntity;

import com.plantops.persistence.entity.PlanVersionEntity;

import com.plantops.persistence.entity.WorkOrderEntity;

import com.plantops.scenario.planning.DetailSchedulePlanningContext;
import com.plantops.scenario.planning.DetailSchedulePlanningContextBuilder;
import com.plantops.scenario.planning.MaterialPlanningContext;

import com.plantops.scenario.planning.DetailScheduleProblemMapper;

import com.plantops.solver.detailschedule.DetailSchedule;

import com.plantops.solver.detailschedule.OperationAssignment;

import com.plantops.solver.detailschedule.ScheduleTimingUtil;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.inject.Inject;

import jakarta.transaction.Transactional;



import java.time.LocalDate;

import java.time.LocalDateTime;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;

import java.util.UUID;

import java.util.concurrent.ExecutionException;



@ApplicationScoped

public class DetailScheduleService {



    @Inject

    SolverRuntimeFactory solverRuntimeFactory;



    @Inject

    ShortageRecommendationService shortageRecommendationService;



    @Inject

    ScheduleFeedbackService scheduleFeedbackService;



    @Inject

    MasterPlanService masterPlanService;



    @Inject

    DetailSchedulePlanningContextBuilder planningContextBuilder;



    @Inject

    DetailScheduleProblemMapper problemMapper;



    @Transactional

    public DetailScheduleResultDto solve(String masterPlanVersionId) throws ExecutionException, InterruptedException {

        return solve(masterPlanVersionId, false, null);

    }



    public DetailScheduleResultDto solve(

            String masterPlanVersionId,

            boolean refreshMasterPlanAfter,

            LocalDate feedbackCutoff) throws ExecutionException, InterruptedException {

        String versionId = "DS-" + UUID.randomUUID().toString().substring(0, 8);

        long start = System.currentTimeMillis();

        DetailSchedulePlanningContext context = buildPlanningContext(masterPlanVersionId);

        return solveWithPlanningContext(context, masterPlanVersionId, refreshMasterPlanAfter, feedbackCutoff, versionId, start);

    }



    public DetailScheduleResultDto solveWithPlanningContext(

            DetailSchedulePlanningContext context,

            String masterPlanVersionId,

            boolean refreshMasterPlanAfter,

            LocalDate feedbackCutoff) throws ExecutionException, InterruptedException {

        String versionId = "DS-" + UUID.randomUUID().toString().substring(0, 8);

        long start = System.currentTimeMillis();

        return solveWithPlanningContext(context, masterPlanVersionId, refreshMasterPlanAfter, feedbackCutoff, versionId, start);

    }



    private DetailScheduleResultDto solveWithPlanningContext(

            DetailSchedulePlanningContext context,

            String masterPlanVersionId,

            boolean refreshMasterPlanAfter,

            LocalDate feedbackCutoff,

            String versionId,

            long start) throws ExecutionException, InterruptedException {

        DetailSchedule problem = problemMapper.toSchedule(context);

        DetailSchedule solution = solveProblem(versionId, problem);

        assignStartTimes(solution);



        long duration = System.currentTimeMillis() - start;

        persistResult(versionId, solution, duration);



        List<DetailScheduleOperationDto> ops = toOperations(solution);

        var shortages = shortageRecommendationService.analyze(solution, versionId);



        MasterPlanRefreshResultDto refresh = null;

        if (refreshMasterPlanAfter

                && masterPlanVersionId != null

                && !masterPlanVersionId.isBlank()) {

            LocalDate cutoff = feedbackCutoff != null ? feedbackCutoff : LocalDate.now();

            scheduleFeedbackService.recordFromDetailSchedule(versionId, masterPlanVersionId, cutoff);

            refresh = masterPlanService.refreshSubsequentPlan(

                    masterPlanVersionId, versionId, cutoff, null);

        }



        return new DetailScheduleResultDto(

                versionId,

                solution.getScore() != null ? solution.getScore().toString() : null,

                duration,

                ops,

                shortages,

                refresh);

    }



    private DetailSchedule solveProblem(String versionId, DetailSchedule problem)

            throws ExecutionException, InterruptedException {

        try (SolverManager<DetailSchedule, String> solver = solverRuntimeFactory.createDetailScheduleSolver()) {

            return solver.solve(versionId, problem).getFinalBestSolution();

        }

    }



    /**
     * 仅执行 S05 推演层（P0–P4），不调用 Timefold；返回诊断快照。
     */
    public DetailSchedulePlanningDiagnosticsDto previewPlanningDiagnostics(String masterPlanVersionId) {
        return buildPlanningContext(masterPlanVersionId).diagnostics();
    }

    public DetailSchedulePlanningContext buildPlanningContext(String masterPlanVersionId) {
        return buildPlanningContext(masterPlanVersionId, null);
    }

    public DetailSchedulePlanningContext buildPlanningContext(
            String masterPlanVersionId,
            MaterialPlanningContext materialPlanning) {
        return planningContextBuilder.build(masterPlanVersionId, materialPlanning);
    }

    private DetailSchedule buildProblem(String masterPlanVersionId) {
        return problemMapper.toSchedule(buildPlanningContext(masterPlanVersionId));
    }



    private void assignStartTimes(DetailSchedule solution) {

        ScheduleTimingUtil.applyLineStartTimes(solution);

    }



    @Transactional

    void persistResult(String versionId, DetailSchedule solution, long durationMs) {

        PlanVersionEntity version = new PlanVersionEntity();

        version.planVersionId = versionId;

        version.planType = "DETAIL_SCHEDULE";

        version.planGeneratedTs = LocalDateTime.now();

        version.changeSource = "APS";

        version.solveDurationMs = durationMs;

        version.score = solution.getScore() != null ? solution.getScore().toString() : null;

        version.stampWorkspace();

        version.persist();



        int seq = 0;

        for (OperationAssignment op : solution.getOperations()) {

            if (op.getLine() == null) {

                continue;

            }

            DetailScheduleOperationEntity row = new DetailScheduleOperationEntity();

            row.planVersionId = versionId;

            row.operationId = op.getOperationId();

            row.workOrderNo = op.getWorkOrderNo();

            row.lineId = op.getLine().getLineId();

            row.sequenceIndex = seq++;

            row.startMinute = op.getStartMinute() != null ? op.getStartMinute() : 0;

            row.endMinute = op.getEndMinute() != null ? op.getEndMinute() : op.getDurationMinutes();

            row.pinned = op.isPinned();

            row.stampWorkspace();

            row.persist();

        }

    }



    private List<DetailScheduleOperationDto> toOperations(DetailSchedule solution) {

        List<OperationAssignment> scheduled = solution.getOperations().stream()

                .filter(op -> op.getLine() != null)

                .sorted(Comparator

                        .comparing((OperationAssignment op) -> resolveResourceId(op))

                        .thenComparing(OperationAssignment::getStartMinute, Comparator.nullsLast(Integer::compareTo))

                        .thenComparing(OperationAssignment::getOperationId))

                .toList();



        java.util.Map<String, Integer> seqByResource = new java.util.HashMap<>();

        List<DetailScheduleOperationDto> rows = new ArrayList<>();

        for (OperationAssignment op : scheduled) {

            String resourceId = resolveResourceId(op);

            int seq = seqByResource.merge(resourceId, 1, Integer::sum);

            rows.add(new DetailScheduleOperationDto(

                    op.getOperationId(),

                    op.getWorkOrderNo(),

                    op.getLine().getLineId(),

                    resourceId,

                    seq,

                    op.getStartMinute(),

                    op.getEndMinute(),

                    op.getProductCode(),

                    op.isPinned()));

        }

        return rows;

    }



    private static String resolveResourceId(OperationAssignment op) {

        if (op.getResourceId() != null && !op.getResourceId().isBlank()) {

            return op.getResourceId();

        }

        if (op.getLine() != null && op.getLine().getResourceId() != null) {

            return op.getLine().getResourceId();

        }

        return op.getLine() != null ? op.getLine().getLineId() : "UNKNOWN";

    }

}


