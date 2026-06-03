package com.plantops.scenario;



import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverManager;

import com.plantops.api.dto.DetailScheduleOperationDto;

import com.plantops.api.dto.DetailScheduleResultDto;

import com.plantops.api.dto.MasterPlanRefreshResultDto;

import com.plantops.api.dto.ShortageRecommendationDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewLineDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewOperationDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewRequest;
import com.plantops.api.dto.planning.ScheduleConstraintViolationDto;

import com.plantops.config.SolverRuntimeFactory;

import com.plantops.persistence.entity.DetailScheduleOperationEntity;

import com.plantops.persistence.entity.PlanVersionEntity;

import com.plantops.persistence.entity.ProductionLineEntity;

import com.plantops.persistence.entity.WorkOrderEntity;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.planning.DetailSchedulePlanningContext;
import com.plantops.scenario.planning.DetailSchedulePlanningContextBuilder;
import com.plantops.scenario.planning.MaterialPlanningContext;
import com.plantops.scenario.planning.DetailScheduleProblemMapper;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.LineChainTimingUtil;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;

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

                solution.score() != null ? solution.score().toString() : null,

                duration,

                ops,

                shortages,

                refresh);

    }



    private DetailSchedule solveProblem(String versionId, DetailSchedule problem)

            throws ExecutionException, InterruptedException {

        try (SolverManager<DetailSchedule> solver = solverRuntimeFactory.createDetailScheduleSolver()) {

            return solver.solve(versionId, problem).getFinalBestSolution();

        }

    }

    /** Session 路径：内存 Timefold，不写库。 */
    public DetailSchedule solveScheduleInMemory(DetailSchedule problem)
            throws ExecutionException, InterruptedException {
        String jobId = "SESSION-" + UUID.randomUUID().toString().substring(0, 8);
        return solveProblem(jobId, problem);
    }

    public void applyTiming(DetailSchedule schedule) {
        assignStartTimes(schedule);
    }

    public List<DetailScheduleOperationDto> operationsFromSchedule(DetailSchedule schedule) {
        return toOperations(schedule);
    }

    @Transactional
    public void persistSchedule(String versionId, DetailSchedule solution, long durationMs) {
        persistResult(versionId, solution, durationMs);
    }

    public DetailSchedulePlanningPreviewDto toSessionPreviewDto(
            DetailSchedulePlanningContext context,
            LocalDateTime computedAt,
            boolean solved,
            boolean persisted,
            boolean initialQueuesSeeded,
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<DetailScheduleOperationDto> scheduledOps) {
        return toSessionPreviewDto(
                context,
                computedAt,
                solved,
                persisted,
                initialQueuesSeeded,
                planVersionId,
                score,
                solveDurationMs,
                scheduledOps,
                List.of(),
                null,
                null,
                List.of());
    }

    public DetailSchedulePlanningPreviewDto toSessionPreviewDto(
            DetailSchedulePlanningContext context,
            LocalDateTime computedAt,
            boolean solved,
            boolean persisted,
            boolean initialQueuesSeeded,
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<DetailScheduleOperationDto> scheduledOps,
            List<ScheduleConstraintViolationDto> violations,
            String simulationMode,
            Long simulationDurationMs,
            List<String> recalculatedOperationIds) {
        return toPreviewDto(
                context,
                computedAt,
                solved,
                persisted,
                initialQueuesSeeded,
                planVersionId,
                score,
                solveDurationMs,
                scheduledOps,
                List.of(),
                violations,
                simulationMode,
                simulationDurationMs,
                recalculatedOperationIds);
    }



    /**
     * 仅执行 S05 推演层（P0–P4），不调用 Timefold；返回诊断快照。
     */
    public DetailSchedulePlanningDiagnosticsDto previewPlanningDiagnostics(String masterPlanVersionId) {
        return buildPlanningContext(masterPlanVersionId).diagnostics();
    }

    /**
     * 推演层统一入口：诊断 + 工序/产线快照；可选内存求解或持久化（结果反写到同一批 {@link OperationAssignment}）。
     */
    public DetailSchedulePlanningPreviewDto previewPlanning(DetailSchedulePlanningPreviewRequest request)
            throws ExecutionException, InterruptedException {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        String masterPlanVersionId = request.masterPlanVersionId();
        if (masterPlanVersionId == null || masterPlanVersionId.isBlank()) {
            throw new BadRequestException("masterPlanVersionId required");
        }
        if (request.resolvePersist() && !request.resolveSolve()) {
            throw new BadRequestException("persist requires solve=true");
        }

        DetailSchedulePlanningContext context = buildPlanningContext(masterPlanVersionId);
        LocalDateTime computedAt = LocalDateTime.now();

        if (request.resolveSolve() && request.resolvePersist()) {
            LocalDate feedbackCutoff = parseFeedbackCutoff(request.feedbackCutoff());
            DetailScheduleResultDto persisted = solveWithPlanningContext(
                    context,
                    masterPlanVersionId,
                    request.resolveRefreshMasterPlanAfter(),
                    feedbackCutoff);
            return toPreviewDto(
                    context,
                    computedAt,
                    true,
                    true,
                    false,
                    persisted.planVersionId(),
                    persisted.score(),
                    persisted.solveDurationMs(),
                    persisted.operations(),
                    persisted.shortageRecommendations());
        }

        if (request.resolveSolve()) {
            long start = System.currentTimeMillis();
            String jobId = "PREVIEW-" + UUID.randomUUID().toString().substring(0, 8);
            DetailSchedule problem = problemMapper.toSchedule(context);
            DetailSchedule solution = solveProblem(jobId, problem);
            assignStartTimes(solution);
            long duration = System.currentTimeMillis() - start;
            return toPreviewDto(
                    context,
                    computedAt,
                    true,
                    false,
                    false,
                    null,
                    solution.score() != null ? solution.score().toString() : null,
                    duration,
                    toOperations(solution),
                    List.of());
        }

        if (request.resolveSeedInitialQueues()) {
            DetailSchedule seeded = problemMapper.toSchedule(context);
            assignStartTimes(seeded);
            return toPreviewDto(
                    context,
                    computedAt,
                    false,
                    false,
                    true,
                    null,
                    null,
                    null,
                    toOperations(seeded),
                    List.of());
        }

        return toPreviewDto(
                context,
                computedAt,
                false,
                false,
                false,
                null,
                null,
                null,
                List.of(),
                List.of());
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
        SolutionManager.updateShadowVariables(solution);
        LineChainTimingUtil.applyAllStartTimes(solution);
    }



    @Transactional

    void persistResult(String versionId, DetailSchedule solution, long durationMs) {

        PlanVersionEntity version = new PlanVersionEntity();

        version.planVersionId = versionId;

        version.planType = "DETAIL_SCHEDULE";

        version.planGeneratedTs = LocalDateTime.now();

        version.changeSource = "APS";

        version.solveDurationMs = durationMs;

        version.score = solution.score() != null ? solution.score().toString() : null;

        version.stampWorkspace();

        version.persist();



        List<OperationAssignment> scheduledOps = solution.getOperations().stream()
                .filter(op -> op.getLine() != null)
                .sorted(Comparator
                        .comparing((OperationAssignment op) -> op.getLine().getLineId())
                        .thenComparing(OperationAssignment::getStartMinute, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(OperationAssignment::getOperationId))
                .toList();

        java.util.Map<String, Integer> seqByLine = new java.util.HashMap<>();
        for (OperationAssignment op : scheduledOps) {
            DetailScheduleOperationEntity row = new DetailScheduleOperationEntity();
            row.planVersionId = versionId;
            row.operationId = op.getOperationId();
            row.workOrderNo = op.getWorkOrderNo();
            row.batchNo = op.getBatchNo();
            row.lineId = op.getLine().getLineId();
            row.sequenceIndex = seqByLine.merge(op.getLine().getLineId(), 1, Integer::sum);
            row.startMinute = op.getStartMinute() != null ? op.getStartMinute() : 0;
            row.endMinute = op.getEndMinute() != null ? op.getEndMinute() : op.getDurationMinutes();
            row.pinned = op.isPinned();
            row.stampWorkspace();
            row.persist();
        }

    }



    private List<DetailScheduleOperationDto> toOperations(DetailSchedule solution) {

        List<OperationAssignment> scheduled = solution.getOperations().stream()

                .filter(op -> lineIdForAssignedOperation(solution, op) != null)

                .filter(op -> op.getStartMinute() != null)

                .sorted(Comparator

                        .comparing((OperationAssignment op) -> lineIdForAssignedOperation(solution, op))

                        .thenComparing(OperationAssignment::getStartMinute, Comparator.nullsLast(Integer::compareTo))

                        .thenComparing(OperationAssignment::getOperationId))

                .toList();



        java.util.Map<String, Integer> seqByLine = new java.util.HashMap<>();

        java.util.Map<String, Integer> changeoverByOp =
                LineChainTimingUtil.changeoverMinutesBeforeByOperationId(solution);

        List<DetailScheduleOperationDto> rows = new ArrayList<>();

        for (OperationAssignment op : scheduled) {

            String lineId = lineIdForAssignedOperation(solution, op);

            int seq = seqByLine.merge(lineId, 1, Integer::sum);

            rows.add(new DetailScheduleOperationDto(

                    op.getOperationId(),

                    op.getWorkOrderNo(),

                    lineId,

                    resolveResourceId(op),

                    seq,

                    op.getStartMinute(),

                    op.getEndMinute(),

                    op.getProductCode(),

                    op.isPinned(),

                    op.getBatchNo(),

                    op.getOperationSeq(),

                    op.getOperationName() != null ? op.getOperationName() : "",

                    changeoverByOp.get(op.getOperationId())));

        }

        return rows;

    }

    /** 以产线 list 为准（与推演引擎一致），不单独依赖可能滞后的 {@code op.getLine()} 影子。 */
    private static String lineIdForAssignedOperation(DetailSchedule schedule, OperationAssignment op) {
        if (schedule.getLines() == null || op == null) {
            return null;
        }
        for (ScheduleLine line : schedule.getLines()) {
            if (line.getAssignedOperations() != null && line.getAssignedOperations().contains(op)) {
                return line.getLineId();
            }
        }
        return null;
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

    /** 从持久化结果加载排程版本（供历史版本查看）。 */
    public DetailScheduleResultDto get(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            throw new NotFoundException("Detail schedule version not found");
        }
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(versionId);
        if (version == null || !"DETAIL_SCHEDULE".equals(version.planType)) {
            throw new NotFoundException("Detail schedule version not found: " + versionId);
        }
        List<DetailScheduleOperationEntity> entities = DetailScheduleOperationEntity.list(
                "workspaceId = ?1 and planVersionId = ?2 order by lineId, sequenceIndex",
                DetailScheduleOperationEntity.ws(),
                versionId);
        List<DetailScheduleOperationDto> ops = new ArrayList<>(entities.size());
        for (DetailScheduleOperationEntity op : entities) {
            String productCode = resolveProductCode(op.workOrderNo);
            int routingSeq = parseOperationSeqFromOperationId(op.operationId);
            ops.add(new DetailScheduleOperationDto(
                    op.operationId,
                    op.workOrderNo,
                    op.lineId,
                    resolveResourceId(op),
                    op.sequenceIndex,
                    op.startMinute,
                    op.endMinute,
                    productCode,
                    op.pinned,
                    op.batchNo,
                    routingSeq,
                    operationNameFor(productCode, routingSeq),
                    null));
        }
        ops = enrichChangeoverMinutesForOperations(ops);
        long duration = version.solveDurationMs != null ? version.solveDurationMs : 0L;
        return new DetailScheduleResultDto(
                versionId,
                version.score,
                duration,
                ops,
                List.of(),
                null);
    }

    private static String resolveResourceId(DetailScheduleOperationEntity op) {
        if (op.lineId != null && !op.lineId.isBlank()) {
            ProductionLineEntity line = ProductionLineEntity.find(
                    "workspaceId = ?1 and lineId = ?2",
                    DetailScheduleOperationEntity.ws(),
                    op.lineId)
                    .firstResult();
            if (line != null && line.resourceId != null && !line.resourceId.isBlank()) {
                return line.resourceId;
            }
        }
        return op.lineId != null ? op.lineId : "UNKNOWN";
    }

    private static String resolveProductCode(String workOrderNo) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        return wo != null && wo.productCode != null ? wo.productCode : "";
    }

    private static int parseOperationSeqFromOperationId(String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return 0;
        }
        int underscore = operationId.lastIndexOf('_');
        if (underscore <= 0) {
            return 0;
        }
        int dash = operationId.lastIndexOf('-', underscore);
        if (dash < 0 || dash >= underscore - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(operationId.substring(dash + 1, underscore));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String operationNameFor(String productCode, int operationSeq) {
        if (productCode == null || productCode.isBlank() || operationSeq <= 0) {
            return "";
        }
        return ProductRoutingSteps.operationsForProduct(productCode).stream()
                .filter(step -> step.sequenceNo() == operationSeq)
                .map(ProductRoutingSteps.Operation::operationName)
                .findFirst()
                .orElse("");
    }

    private static LocalDate parseFeedbackCutoff(String feedbackCutoff) {
        if (feedbackCutoff == null || feedbackCutoff.isBlank()) {
            return null;
        }
        return LocalDate.parse(feedbackCutoff);
    }

    private DetailSchedulePlanningPreviewDto toPreviewDto(
            DetailSchedulePlanningContext context,
            LocalDateTime computedAt,
            boolean solved,
            boolean persisted,
            boolean initialQueuesSeeded,
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<DetailScheduleOperationDto> scheduledOps,
            List<ShortageRecommendationDto> shortages) {
        return toPreviewDto(
                context,
                computedAt,
                solved,
                persisted,
                initialQueuesSeeded,
                planVersionId,
                score,
                solveDurationMs,
                scheduledOps,
                shortages,
                List.of(),
                null,
                null,
                List.of());
    }

    private DetailSchedulePlanningPreviewDto toPreviewDto(
            DetailSchedulePlanningContext context,
            LocalDateTime computedAt,
            boolean solved,
            boolean persisted,
            boolean initialQueuesSeeded,
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<DetailScheduleOperationDto> scheduledOps,
            List<ShortageRecommendationDto> shortages,
            List<ScheduleConstraintViolationDto> violations,
            String simulationMode,
            Long simulationDurationMs,
            List<String> recalculatedOperationIds) {
        List<DetailSchedulePlanningPreviewOperationDto> operations =
                buildPreviewOperations(context.operations(), scheduledOps);
        List<DetailSchedulePlanningPreviewLineDto> lines = buildPreviewLines(context.lines());
        int scheduledCount = (int) operations.stream().filter(DetailSchedulePlanningPreviewOperationDto::scheduled).count();
        return new DetailSchedulePlanningPreviewDto(
                computedAt,
                context.planningAnchor(),
                context.diagnostics() != null ? context.diagnostics().masterPlanVersionId() : null,
                solved,
                persisted,
                initialQueuesSeeded,
                planVersionId,
                score,
                solveDurationMs,
                context.diagnostics(),
                lines,
                operations,
                operations.size(),
                scheduledCount,
                shortages != null ? shortages : List.of(),
                violations != null ? violations : List.of(),
                simulationMode,
                simulationDurationMs,
                recalculatedOperationIds != null ? recalculatedOperationIds : List.of());
    }

    private static List<DetailSchedulePlanningPreviewLineDto> buildPreviewLines(List<ScheduleLine> lines) {
        if (lines == null) {
            return List.of();
        }
        List<DetailSchedulePlanningPreviewLineDto> out = new ArrayList<>(lines.size());
        for (ScheduleLine line : lines) {
            int queued = line.getAssignedOperations() != null ? line.getAssignedOperations().size() : 0;
            out.add(new DetailSchedulePlanningPreviewLineDto(
                    line.getLineId(),
                    line.getResourceId(),
                    line.getAreaId(),
                    line.isOpened(),
                    line.getCapacityMinutes(),
                    queued));
        }
        return out;
    }

    private static List<DetailSchedulePlanningPreviewOperationDto> buildPreviewOperations(
            List<OperationAssignment> candidates,
            List<DetailScheduleOperationDto> scheduledOps) {
        java.util.Map<String, DetailScheduleOperationDto> scheduledById = new java.util.HashMap<>();
        if (scheduledOps != null) {
            for (DetailScheduleOperationDto row : scheduledOps) {
                scheduledById.put(row.operationId(), row);
            }
        }
        if (candidates == null) {
            return List.of();
        }
        List<DetailSchedulePlanningPreviewOperationDto> out = new ArrayList<>(candidates.size());
        for (OperationAssignment op : candidates) {
            DetailScheduleOperationDto scheduled = scheduledById.get(op.getOperationId());
            String lineId = scheduled != null ? scheduled.lineId() : null;
            Integer start = scheduled != null ? scheduled.startMinute() : null;
            Integer end = scheduled != null ? scheduled.endMinute() : null;
            Integer seqOnLine = scheduled != null ? scheduled.sequenceIndex() : null;
            boolean onLine = scheduled != null;
            out.add(new DetailSchedulePlanningPreviewOperationDto(
                    op.getOperationId(),
                    op.getWorkOrderNo(),
                    op.getBatchNo(),
                    op.getProductCode(),
                    op.getOperationName(),
                    op.getOperationSeq(),
                    resolveResourceId(op),
                    lineId,
                    seqOnLine,
                    start,
                    end,
                    onLine,
                    op.isKittingEligible(),
                    op.getEarliestStartMinute(),
                    op.isPinned(),
                    op.getMpContractStartDate(),
                    op.getMpContractEndDate(),
                    op.getMpTargetEndDate(),
                    scheduled != null ? scheduled.changeoverMinutesBefore() : null));
        }
        out.sort(java.util.Comparator
                .comparing(DetailSchedulePlanningPreviewOperationDto::workOrderNo, java.util.Comparator.nullsLast(String::compareTo))
                .thenComparingInt(DetailSchedulePlanningPreviewOperationDto::operationSeq)
                .thenComparing(DetailSchedulePlanningPreviewOperationDto::operationId));
        return out;
    }

    /** 持久化排程结果无 Session 队列时，按产线顺序与换型矩阵补算换型分钟。 */
    private static List<DetailScheduleOperationDto> enrichChangeoverMinutesForOperations(
            List<DetailScheduleOperationDto> operations) {
        if (operations == null || operations.isEmpty()) {
            return operations != null ? operations : List.of();
        }
        ChangeoverRuleIndex changeoverRules = ChangeoverRuleIndex.fromWorkspace();
        java.util.Map<String, List<DetailScheduleOperationDto>> byLine = new java.util.LinkedHashMap<>();
        for (DetailScheduleOperationDto op : operations) {
            if (op.lineId() == null || op.lineId().isBlank()) {
                continue;
            }
            byLine.computeIfAbsent(op.lineId(), k -> new ArrayList<>()).add(op);
        }
        java.util.Map<String, Integer> changeoverByOp = new java.util.HashMap<>();
        for (List<DetailScheduleOperationDto> lineOps : byLine.values()) {
            lineOps.sort(Comparator
                    .comparingInt((DetailScheduleOperationDto op) ->
                            op.startMinute() != null ? op.startMinute() : 0)
                    .thenComparingInt(DetailScheduleOperationDto::sequenceIndex));
            DetailScheduleOperationDto previous = null;
            for (DetailScheduleOperationDto op : lineOps) {
                if (previous != null) {
                    int minutes = changeoverRules.computeMinutes(
                            op.operationName(),
                            op.resourceId(),
                            op.operationSeq(),
                            previous.productCode(),
                            op.productCode());
                    if (minutes > 0) {
                        changeoverByOp.put(op.operationId(), minutes);
                    }
                }
                previous = op;
            }
        }
        List<DetailScheduleOperationDto> enriched = new ArrayList<>(operations.size());
        for (DetailScheduleOperationDto op : operations) {
            enriched.add(new DetailScheduleOperationDto(
                    op.operationId(),
                    op.workOrderNo(),
                    op.lineId(),
                    op.resourceId(),
                    op.sequenceIndex(),
                    op.startMinute(),
                    op.endMinute(),
                    op.productCode(),
                    op.pinned(),
                    op.batchNo(),
                    op.operationSeq(),
                    op.operationName(),
                    changeoverByOp.get(op.operationId())));
        }
        return enriched;
    }

}


