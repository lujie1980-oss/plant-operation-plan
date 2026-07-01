package com.plantops.scenario;

import ai.timefold.solver.core.api.score.HardSoftScore;
import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.LineOpeningDecisionDto;
import com.plantops.api.dto.LoadBucketDto;
import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.api.dto.MasterPlanRefreshResultDto;
import com.plantops.api.dto.MasterPlanResultDto;
import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewAllocationDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewRequest;
import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.api.dto.WorkOrderCapacityBucketDto;
import com.plantops.api.dto.WorkOrderCapacityGanttDto;
import com.plantops.api.dto.WorkOrderCapacityOperationDto;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.config.ParameterRegistry;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.persistence.entity.LineOpeningDecisionEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ScheduleFeedbackEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.planning.MasterPlanOntologyScheduleBuilder;
import com.plantops.scenario.planning.MasterPlanPlanningContext;
import com.plantops.scenario.planning.MaterialPlanningContext;
import com.plantops.scenario.planning.MasterPlanProblemMapper;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.solver.masterplan.MasterPlanObjectiveSettings;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.scenario.planning.JitResourceCapacitySeeder;
import com.plantops.scenario.planning.ResourceCapacityResultProjector;
import com.plantops.scenario.planning.optimizer.MasterPlanScheduleOptimizerApplicator;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerException;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerRegistry;
import com.plantops.scenario.planning.optimizer.PlanningProblem;
import com.plantops.scenario.planning.optimizer.timefold.MasterPlanTimefoldSolver;
import com.plantops.scenario.planning.optimizer.timefold.TimefoldPlanningOptimizer;
import com.plantops.scenario.planning.optimizer.ortools.OrtoolsResourceCapacityCpSolver;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.SlotFixedLoad;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.sample.SampleDataLoader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class MasterPlanService {

    /** 成品工单：销售订单层根工单（无父工单） */
    public static final String SOURCE_EXTERNAL = "EXTERNAL";
    /** 组件工单：BOM 展开的下级工单（有父工单） */
    public static final String SOURCE_REPLENISH = "REPLENISH";

    private static final int DEFAULT_SHIFT_MINUTES = 480;

    /**
     * 工艺路由步骤：优先从 product_resource 表中按 sequence_no 取出；
     * 当数据库没有维护任何工序时回退到 {@link ProductRoutingCatalog}。
     */
    public record RoutingStep(
            int sequenceNo,
            String operationName,
            String resourceId,
            BigDecimal processTimeSeconds,
            List<String> allowedResourceIds) {
    }

    private List<RoutingStep> routingStepsFor(String productCode, List<MasterPlanAllocationEntity> allocRows) {
        List<ProductRoutingSteps.Operation> operations = ProductRoutingSteps.operationsForProduct(productCode);
        if (operations.isEmpty()) {
            List<ProductRoutingCatalog.RoutingStep> fallback = ProductRoutingCatalog.stepsFor(productCode);
            List<RoutingStep> out = new ArrayList<>(fallback.size());
            for (int i = 0; i < fallback.size(); i++) {
                ProductRoutingCatalog.RoutingStep s = fallback.get(i);
                out.add(new RoutingStep(
                        i + 1, s.operationName(), s.resourceId(), null, List.of(s.resourceId())));
            }
            return out;
        }
        Map<Integer, String> assignedResourceBySeq = assignedResourceByOpSeq(allocRows);
        List<RoutingStep> out = new ArrayList<>(operations.size());
        for (ProductRoutingSteps.Operation op : operations) {
            String resourceId = assignedResourceBySeq.getOrDefault(op.sequenceNo(), op.primaryResourceId());
            ProductResourceEntity row = ProductResourceEntity.findByProductAndResource(productCode, resourceId);
            BigDecimal processTime = row != null ? row.processTimeSeconds : op.primaryProcessTimeSeconds();
            out.add(new RoutingStep(
                    op.sequenceNo(),
                    op.operationName(),
                    resourceId,
                    processTime,
                    op.allowedResourceIds()));
        }
        return out;
    }

    private static Map<Integer, String> assignedResourceByOpSeq(List<MasterPlanAllocationEntity> rows) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (MasterPlanAllocationEntity row : rows) {
            Integer seq = parseOpSeqFromAllocationId(row.allocationId);
            if (seq != null && row.resourceId != null && !row.resourceId.isBlank()) {
                map.putIfAbsent(seq, row.resourceId);
            }
        }
        return map;
    }

    @Inject
    ParameterRegistry parameters;

    @Inject
    PlanningOptimizerRegistry optimizerRegistry;

    @Inject
    MasterPlanTimefoldSolver timefoldSolver;

    @Inject
    CapacityService capacityService;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    WorkOrderTimingService workOrderTimingService;

    @Inject
    MasterPlanOntologyScheduleBuilder ontologyScheduleBuilder;

    @Inject
    MasterPlanProblemMapper problemMapper;

    @Inject
    JitResourceCapacitySeeder jitResourceCapacitySeeder;

    public MasterPlanResultDto solve() throws ExecutionException, InterruptedException {
        return solveWithStrategy(null);
    }

    /**
     * 按策略求解（产能模式 + 优化目标权重均来自策略配置）。
     */
    public MasterPlanResultDto solveWithStrategy(String strategyId)
            throws ExecutionException, InterruptedException {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                strategyId != null && !strategyId.isBlank() ? strategyId : null);
        return solveInternal(resolved);
    }

    /**
     * 求解在事务外执行（避免 30s+ 求解导致 JTA 超时）；结果分步短事务持久化。
     * @deprecated 请使用 {@link #solveWithStrategy(String)}
     */
    public MasterPlanResultDto solve(MasterPlanCapacityStrategy strategy)
            throws ExecutionException, InterruptedException {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved =
                strategyConfigService.resolveFromRequest(null, strategy != null ? strategy.name() : null);
        return solveInternal(resolved);
    }

    private MasterPlanResultDto solveInternal(MasterPlanStrategyConfigService.ResolvedStrategy resolved)
            throws ExecutionException, InterruptedException {
        sampleDataLoader.extendCalendarsToHorizon();
        MasterPlanPlanningContext context = buildPlanningContext(resolved, MasterPlanCapacityOverlay.empty());
        return solveWithPlanningContext(context, resolved, null, null);
    }

    /**
     * 构建 S04 推演快照（P0–P4），不含 Timefold。
     */
    public MasterPlanPlanningContext buildPlanningContext(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay) {
        return buildPlanningContext(resolved, capacityOverlay, null);
    }

    public MasterPlanPlanningContext buildPlanningContext(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay,
            MaterialPlanningContext materialPlanning) {
        return ontologyScheduleBuilder.buildPlanningContext(resolved, capacityOverlay, materialPlanning);
    }

    /**
     * 由已构建的推演上下文投影并求解、持久化。
     */
    public MasterPlanResultDto solveWithPlanningContext(
            MasterPlanPlanningContext context,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            String parentMasterPlanVersionId,
            String detailScheduleVersionId) throws ExecutionException, InterruptedException {
        String versionId = "MP-" + UUID.randomUUID().toString().substring(0, 8);
        long start = System.currentTimeMillis();
        MasterPlanSchedule problem = problemMapper.toSchedule(context);
        MasterPlanSchedule solution = solveProblem(problem);
        long duration = System.currentTimeMillis() - start;
        persistResult(versionId, solution, duration, resolved, parentMasterPlanVersionId, detailScheduleVersionId);
        persistLineOpenings(versionId);
        return getResult(versionId);
    }

    /**
     * 按反馈截止日构建产能 overlay（与 {@link #refreshSubsequentPlan} 一致）。
     */
    public MasterPlanCapacityOverlay buildFeedbackOverlay(LocalDate feedbackCutoff) {
        LocalDate cutoff = feedbackCutoff != null ? feedbackCutoff : LocalDate.now();
        LocalDate start = LocalDate.now();
        List<TimeSlot> slots = timeslotHorizonService.buildSlots(start, ProductionResourceEntity.routingResourceIds());
        List<SlotFixedLoad> fixedLoads = scheduleFeedbackService.buildFixedLoadsForSlots(slots, cutoff);
        return MasterPlanCapacityOverlay.fromFixedLoads(fixedLoads, cutoff);
    }

    /**
     * 将基线主计划中「非本交付链」工单的 allocation 固定到产能槽，供单交付有限能力求解占用剩余产能。
     */
    public MasterPlanCapacityOverlay buildBaselineOverlayExcludingWorkOrders(
            String baselinePlanVersionId,
            Set<String> replannableWorkOrderNos,
            List<TimeSlot> slots) {
        if (baselinePlanVersionId == null || baselinePlanVersionId.isBlank() || slots == null || slots.isEmpty()) {
            return MasterPlanCapacityOverlay.empty();
        }
        Set<String> scope = replannableWorkOrderNos != null ? replannableWorkOrderNos : Set.of();
        Map<String, Integer> minutesBySlotId = new LinkedHashMap<>();
        for (MasterPlanAllocationEntity alloc : MasterPlanAllocationEntity
                .find("planVersionId", baselinePlanVersionId)
                .<MasterPlanAllocationEntity>list()) {
            if (alloc.workOrderNo != null && scope.contains(alloc.workOrderNo)) {
                continue;
            }
            if (alloc.resourceId == null || alloc.slotDate == null || alloc.durationMinutes == null) {
                continue;
            }
            TimeSlot slot = scheduleFeedbackService.resolveSlot(slots, alloc.resourceId, alloc.slotDate);
            if (slot == null) {
                continue;
            }
            minutesBySlotId.merge(slot.getId(), alloc.durationMinutes, Integer::sum);
        }
        List<SlotFixedLoad> loads = new ArrayList<>();
        minutesBySlotId.forEach((slotId, minutes) -> loads.add(new SlotFixedLoad(slotId, minutes)));
        return MasterPlanCapacityOverlay.fromFixedLoads(loads, null);
    }

    public record InMemorySolveResult(
            MasterPlanSchedule solution,
            String score,
            long solveDurationMs) {
    }

    /**
     * 内存求解（不持久化），与 {@link #previewPlanning} 中 solve=true、persist=false 一致。
     */
    public InMemorySolveResult solveInMemory(MasterPlanPlanningContext context)
            throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        MasterPlanSchedule problem = problemMapper.toSchedule(context);
        MasterPlanSchedule solution = solveProblem(problem);
        long duration = System.currentTimeMillis() - start;
        String score = solution.score() != null ? solution.score().toString() : null;
        return new InMemorySolveResult(solution, score, duration);
    }

    /** 直驱路径：对已投影的 {@link MasterPlanSchedule} 内存求解（不持久化）。 */
    public InMemorySolveResult solveInMemory(MasterPlanSchedule problem)
            throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        MasterPlanSchedule solution = solveProblem(problem);
        long duration = System.currentTimeMillis() - start;
        String score = solution.score() != null ? solution.score().toString() : null;
        return new InMemorySolveResult(solution, score, duration);
    }

    private MasterPlanSchedule solveProblem(MasterPlanSchedule problem)
            throws ExecutionException, InterruptedException {
        if (problem.hasResourceCapacityAssignments()) {
            return solveResourceCapacityProblem(problem);
        }
        String engineId = parameters.get(PlanningOptimizerRegistry.PARAM_ENGINE);
        if (engineId != null && TimefoldPlanningOptimizer.ENGINE_ID.equalsIgnoreCase(engineId.trim())) {
            return timefoldSolver.solve(problem);
        }
        try {
            OptimizerResult result = optimizerRegistry.requireDefault().optimize(
                    PlanningProblem.forOntologySchedule(problem, "MP-SOLVE-" + UUID.randomUUID()));
            return MasterPlanScheduleOptimizerApplicator.apply(problem, result);
        } catch (PlanningOptimizerException ex) {
            throw new ExecutionException(ex.getMessage(), ex);
        }
    }

    private MasterPlanSchedule solveResourceCapacityProblem(MasterPlanSchedule problem)
            throws ExecutionException {
        jitResourceCapacitySeeder.seedIfEnabled(problem);
        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(problem, null);
        if (!outcome.feasible()) {
            throw new ExecutionException(
                    "OR-Tools multi-resource master plan infeasible: " + outcome.scoreSummary(),
                    null);
        }
        problem.setResourceCapacityAssignments(outcome.assigned());
        int softPenalty = -outcome.capacityOverloadMinutes();
        if (outcome.scoreSummary().contains("(relaxed:")) {
            softPenalty -= 1;
        }
        problem.setScore(HardSoftScore.of(0, softPenalty));
        return problem;
    }

    /**
     * 排程反馈后滚动更新主计划：cutoff 及之前遵循排程冻结结果，之后槽位重新求解。
     */
    public MasterPlanRefreshResultDto refreshSubsequentPlan(
            String parentMasterPlanVersionId,
            String detailScheduleVersionId,
            LocalDate feedbackCutoff,
            String strategyId) throws ExecutionException, InterruptedException {
        if (parentMasterPlanVersionId == null || parentMasterPlanVersionId.isBlank()) {
            throw new IllegalArgumentException("parentMasterPlanVersionId is required");
        }
        if (detailScheduleVersionId == null || detailScheduleVersionId.isBlank()) {
            throw new IllegalArgumentException("detailScheduleVersionId is required");
        }
        PlanVersionEntity parent = PlanVersionEntity.findByVersionId(parentMasterPlanVersionId);
        if (parent == null || !"MASTER_PLAN".equals(parent.planType)) {
            throw new NotFoundException("Master plan version not found: " + parentMasterPlanVersionId);
        }
        LocalDate cutoff = feedbackCutoff != null ? feedbackCutoff : LocalDate.now();
        if (ScheduleFeedbackEntity.listForDetailSchedule(detailScheduleVersionId).isEmpty()) {
            scheduleFeedbackService.recordFromDetailSchedule(
                    detailScheduleVersionId, parentMasterPlanVersionId, cutoff);
        }

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                strategyId != null && !strategyId.isBlank() ? strategyId : parent.strategyId);
        sampleDataLoader.extendCalendarsToHorizon();

        LocalDate start = LocalDate.now();
        List<TimeSlot> slots = timeslotHorizonService.buildSlots(start, ProductionResourceEntity.routingResourceIds());
        List<SlotFixedLoad> fixedLoads = scheduleFeedbackService.buildFixedLoadsForSlots(slots, cutoff);
        MasterPlanCapacityOverlay overlay = MasterPlanCapacityOverlay.fromFixedLoads(fixedLoads, cutoff);

        String versionId = "MP-" + UUID.randomUUID().toString().substring(0, 8);
        long solveStart = System.currentTimeMillis();
        MasterPlanSchedule problem = buildProblem(resolved, overlay);
        MasterPlanSchedule solution = solveProblem(problem);
        long duration = System.currentTimeMillis() - solveStart;

        int frozenRows = persistFrozenFeedbackAllocations(versionId, slots, cutoff);
        persistResult(versionId, solution, duration, resolved, parentMasterPlanVersionId, detailScheduleVersionId);
        persistLineOpenings(versionId);
        if (parent.scenarioId != null && !parent.scenarioId.isBlank()) {
            planningScenarioService.recordMasterPlanVersion(parent.scenarioId, versionId);
        }

        int replannedRows = (int) MasterPlanAllocationEntity.count(
                "planVersionId = ?1", versionId) - frozenRows;
        return new MasterPlanRefreshResultDto(
                versionId,
                parentMasterPlanVersionId,
                detailScheduleVersionId,
                cutoff,
                frozenRows,
                Math.max(0, replannedRows));
    }

    public List<MasterPlanAllocationDto> allocationsForPlanVersion(String planVersionId) {
        if (planVersionId == null || planVersionId.isBlank()) {
            return List.of();
        }
        return MasterPlanAllocationEntity
                .find("planVersionId", planVersionId).<MasterPlanAllocationEntity>list().stream()
                .map(this::toAllocationDto)
                .toList();
    }

    public MasterPlanResultDto getResult(String versionId) {
        PlanVersionEntity v = PlanVersionEntity.findByVersionId(versionId);
        if (v == null) {
            return null;
        }
        List<MasterPlanAllocationDto> allocations = allocationsForPlanVersion(versionId);
        List<LineOpeningDecisionDto> openings = LineOpeningDecisionEntity
                .find("planVersionId", versionId).<LineOpeningDecisionEntity>list().stream()
                .map(o -> new LineOpeningDecisionDto(
                        o.areaId, o.lineId, o.shiftId, o.calendarDate, o.opened,
                        o.suggestedHeadcount != null ? o.suggestedHeadcount : 0))
                .toList();
        return new MasterPlanResultDto(
                versionId,
                v.score,
                v.solveDurationMs,
                v.capacityStrategy != null ? v.capacityStrategy : MasterPlanCapacityStrategy.UNCONSTRAINED.name(),
                v.strategyId,
                v.strategyName,
                buildKpis(v.score, allocations),
                allocations,
                openings);
    }

    /**
     * 针对选定工单，构建产能甘特数据：
     * - 工序步骤（按产品工艺路由展开）及对应机台
     * - 每个机台的产能利用率（来自最近一次产能分析）
     * - 工单计划开始 → 计划结束的时间窗口
     */
    public WorkOrderCapacityGanttDto getWorkOrderCapacityGantt(String versionId, String workOrderNo) {
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(versionId);
        if (version == null) {
            throw new NotFoundException("Plan version not found: " + versionId);
        }
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            throw new NotFoundException("Work order not found: " + workOrderNo);
        }
        WorkOrderPlannedWindow window = resolveWorkOrderWindow(versionId, workOrderNo);
        List<MasterPlanAllocationEntity> allocRows = MasterPlanAllocationEntity
                .find("planVersionId = ?1 and workOrderNo = ?2 order by slotDate, slotIndex", versionId, workOrderNo)
                .list();
        LocalDateTime plannedStart = window != null ? window.plannedStart() : LocalDate.now().atTime(8, 0);
        LocalDateTime plannedEnd = window != null ? window.plannedEnd() : plannedStart.plusMinutes(workOrderMinutes(wo));
        int totalDuration = sumAllocationMinutes(allocRows);
        if (totalDuration <= 0) {
            totalDuration = window != null
                    ? (int) java.time.Duration.between(plannedStart, plannedEnd).toMinutes()
                    : workOrderMinutes(wo);
        }

        List<RoutingStep> steps = routingStepsFor(wo.productCode, allocRows);
        Map<Integer, List<MasterPlanAllocationEntity>> allocsByOpSeq = groupAllocationsByOpSeq(allocRows);
        List<WorkOrderCapacityOperationDto> operations = buildCapacityOperationsFromAllocations(
                workOrderNo,
                wo.productCode,
                steps,
                allocsByOpSeq,
                plannedStart,
                plannedEnd,
                totalDuration,
                businessRuleScopeService.loadTransferTimeIndex());

        List<WorkOrderCapacityBucketDto> resourceBuckets = buildResourceBuckets(
                operations,
                plannedStart.toLocalDate(),
                resolvePlanHorizonEndDate());

        LocalDateTime horizonStartTs = plannedStart;
        LocalDateTime horizonEndTs = resolvePlanHorizonEndDate().atTime(17, 0);
        if (!horizonEndTs.isAfter(horizonStartTs)) {
            horizonEndTs = plannedEnd.plusDays(7);
        }
        WorkOrderTimingWindowDto timingWindow = workOrderTimingService.compute(workOrderNo, versionId);
        String source = wo.bomLevel == 0 ? SOURCE_EXTERNAL : SOURCE_REPLENISH;

        return new WorkOrderCapacityGanttDto(
                wo.workOrderNo,
                wo.parentWorkOrderNo,
                source,
                wo.productCode,
                wo.quantity,
                wo.salesOrderNo,
                wo.salesOrderLineNo,
                plannedStart,
                plannedEnd,
                totalDuration,
                horizonStartTs,
                horizonEndTs,
                timingWindow,
                operations,
                resourceBuckets);
    }

    private LocalDate resolvePlanHorizonEndDate() {
        return LocalDate.now().plusDays(Math.max(1, timeslotHorizonService.totalCalendarDays()) - 1L);
    }

    public record WorkOrderPlannedWindow(
            LocalDateTime plannedStart,
            LocalDateTime plannedEnd,
            LocalDate slotDate,
            String shiftId,
            String resourceId) {
    }

    /** 解析指定主计划场景下工单的计划起止时间（含跨槽拆段）。 */
    public WorkOrderPlannedWindow resolveWorkOrderWindow(String versionId, String workOrderNo) {
        if (versionId == null || versionId.isBlank() || workOrderNo == null || workOrderNo.isBlank()) {
            return null;
        }
        if (PlanVersionEntity.findByVersionId(versionId) == null) {
            return null;
        }
        WorkOrderEntity wo = WorkOrderEntity.findByNo(workOrderNo);
        if (wo == null) {
            return null;
        }
        List<MasterPlanAllocationEntity> allocRows = MasterPlanAllocationEntity
                .find("planVersionId = ?1 and workOrderNo = ?2 order by slotDate, slotIndex", versionId, workOrderNo)
                .list();
        if (allocRows.isEmpty()) {
            return null;
        }
        MasterPlanAllocationEntity first = allocRows.get(0);
        MasterPlanAllocationEntity last = allocRows.get(allocRows.size() - 1);
        int totalDuration = allocRows.stream()
                .mapToInt(a -> a.durationMinutes != null ? a.durationMinutes : 0)
                .filter(m -> m > 0)
                .sum();
        if (totalDuration <= 0) {
            totalDuration = workOrderMinutes(wo);
        }
        LocalDateTime plannedStart = shiftStart(first.slotDate, first.shiftId);
        int lastMinutes = last.durationMinutes != null && last.durationMinutes > 0
                ? last.durationMinutes
                : Math.max(1, totalDuration / allocRows.size());
        LocalDateTime plannedEnd = shiftStart(last.slotDate, last.shiftId).plusMinutes(lastMinutes);
        String resourceId = last.resourceId != null ? last.resourceId : wo.resourceId;
        return new WorkOrderPlannedWindow(
                plannedStart, plannedEnd, last.slotDate, last.shiftId, resourceId);
    }

    public MasterPlanAllocationEntity findAllocationForOrderLine(
            String versionId, String salesOrderNo, int salesOrderLineNo) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        return MasterPlanAllocationEntity
                .find(
                        "planVersionId = ?1 and salesOrderNo = ?2 and salesOrderLineNo = ?3 order by slotDate, slotIndex",
                        versionId,
                        salesOrderNo,
                        salesOrderLineNo)
                .firstResult();
    }

    private List<WorkOrderCapacityBucketDto> buildResourceBuckets(
            List<WorkOrderCapacityOperationDto> operations,
            LocalDate axisStartDate,
            LocalDate axisEndDate) {
        java.util.LinkedHashSet<String> resourceIds = new java.util.LinkedHashSet<>();
        for (WorkOrderCapacityOperationDto op : operations) {
            if (op.allowedResourceIds() != null && !op.allowedResourceIds().isEmpty()) {
                resourceIds.addAll(op.allowedResourceIds());
            } else if (op.resourceId() != null) {
                resourceIds.add(op.resourceId());
            }
        }
        LocalDate horizonStart = axisStartDate;
        LocalDate horizonEnd = axisEndDate;
        if (horizonEnd.isBefore(horizonStart)) {
            horizonEnd = horizonStart.plusDays(7);
        }

        CapacityAnalysisDto capacity = capacityService.analyze();
        Map<String, List<LoadBucketDto>> bucketsByResource = new LinkedHashMap<>();
        for (LoadBucketDto bucket : capacity.loadBuckets()) {
            if (!resourceIds.contains(bucket.resourceId())) {
                continue;
            }
            if (bucket.date().isBefore(horizonStart) || bucket.date().isAfter(horizonEnd)) {
                continue;
            }
            bucketsByResource.computeIfAbsent(bucket.resourceId(), k -> new ArrayList<>()).add(bucket);
        }

        List<WorkOrderCapacityBucketDto> result = new ArrayList<>();
        for (String resourceId : resourceIds) {
            List<LoadBucketDto> rows = bucketsByResource.getOrDefault(resourceId, List.of());
            if (rows.isEmpty()) {
                rows = buildFallbackBuckets(resourceId, horizonStart, horizonEnd);
            }
            for (LoadBucketDto row : rows) {
                result.add(new WorkOrderCapacityBucketDto(
                        row.resourceId(),
                        row.date(),
                        row.shiftId(),
                        row.demandMinutes(),
                        row.availableMinutes(),
                        row.utilizationPct(),
                        row.overloaded()));
            }
        }
        return result;
    }

    private List<LoadBucketDto> buildFallbackBuckets(String resourceId, LocalDate start, LocalDate end) {
        List<LoadBucketDto> rows = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            int cap = timeslotHorizonService.capacityForDay(resourceId, d);
            ResourceCalendarEntity cal = ResourceCalendarEntity
                    .find("resourceId = ?1 and calendarDate = ?2", resourceId, d)
                    .firstResult();
            String shift = cal != null ? cal.shiftId : "DAY";
            rows.add(new LoadBucketDto(
                    resourceId + "|" + d + "|" + shift,
                    resourceId,
                    resourceId,
                    d,
                    shift,
                    0,
                    0,
                    cap,
                    0,
                    false,
                    List.of()));
            d = d.plusDays(1);
        }
        return rows;
    }

    /**
     * 推演层统一入口：诊断 + 分配候选快照；可选内存求解或持久化（结果反写到同一批 {@link OrderAllocation}）。
     */
    public MasterPlanPlanningPreviewDto previewPlanning(MasterPlanPlanningPreviewRequest request)
            throws ExecutionException, InterruptedException {
        if (request == null) {
            throw new BadRequestException("request body required");
        }
        if (request.resolvePersist() && !request.resolveSolve()) {
            throw new BadRequestException("persist requires solve=true");
        }

        MasterPlanStrategyConfigService.ResolvedStrategy resolved = strategyConfigService.resolve(
                request.strategyId() != null && !request.strategyId().isBlank() ? request.strategyId() : null);
        sampleDataLoader.extendCalendarsToHorizon();
        LocalDate feedbackCutoff = parseFeedbackCutoff(request.feedbackCutoff());
        MasterPlanCapacityOverlay overlay = feedbackCutoff != null
                ? buildFeedbackOverlay(feedbackCutoff)
                : MasterPlanCapacityOverlay.empty();
        MasterPlanPlanningContext context = buildPlanningContext(resolved, overlay);
        LocalDateTime computedAt = LocalDateTime.now();
        boolean overlayActive = feedbackCutoff != null;

        if (request.resolveSolve() && request.resolvePersist()) {
            MasterPlanResultDto persisted = solveWithPlanningContext(context, resolved, null, null);
            return toPreviewDto(
                    context,
                    resolved,
                    computedAt,
                    overlayActive,
                    true,
                    true,
                    persisted.planVersionId(),
                    persisted.score(),
                    persisted.solveDurationMs(),
                    persisted.allocations());
        }

        if (request.resolveSolve()) {
            long start = System.currentTimeMillis();
            MasterPlanSchedule problem = problemMapper.toSchedule(context);
            MasterPlanSchedule solution = solveProblem(problem);
            long duration = System.currentTimeMillis() - start;
            return toPreviewDto(
                    context,
                    resolved,
                    computedAt,
                    overlayActive,
                    true,
                    false,
                    null,
                    solution.score() != null ? solution.score().toString() : null,
                    duration,
                    allocationsFromSolution(solution));
        }

        return toPreviewDto(
                context,
                resolved,
                computedAt,
                overlayActive,
                false,
                false,
                null,
                null,
                null,
                List.of());
    }

    private MasterPlanSchedule buildProblem(
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            MasterPlanCapacityOverlay capacityOverlay) {
        return ontologyScheduleBuilder.buildSchedule(resolved, capacityOverlay, null, null);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    int persistFrozenFeedbackAllocations(String planVersionId, List<TimeSlot> slots, LocalDate cutoff) {
        int count = 0;
        for (ScheduleFeedbackEntity fb : ScheduleFeedbackEntity.listFrozenUpTo(cutoff)) {
            TimeSlot slot = scheduleFeedbackService.resolveSlot(slots, fb.resourceId, fb.slotDate);
            if (slot == null) {
                continue;
            }
            WorkOrderEntity wo = WorkOrderEntity.findByNo(fb.workOrderNo);
            MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
            row.planVersionId = planVersionId;
            row.allocationId = "FB-" + fb.operationId;
            row.workOrderNo = fb.workOrderNo;
            row.productCode = wo != null ? wo.productCode : null;
            row.salesOrderNo = wo != null ? wo.salesOrderNo : null;
            row.salesOrderLineNo = wo != null ? wo.salesOrderLineNo : 0;
            row.resourceId = fb.resourceId;
            row.slotIndex = slot.getIndex();
            row.slotDate = fb.slotDate;
            row.shiftId = slot.getShiftId();
            row.durationMinutes = fb.durationMinutes;
            row.stampWorkspace();
            row.persist();
            count++;
        }
        return count;
    }

    /**
     * 持久化内存求解结果（本体 Session confirm / 直驱路径）。
     *
     * @return 新主计划版本 ID
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String persistFromSchedule(
            MasterPlanSchedule solution,
            long solveDurationMs,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            String parentPlanVersionId) {
        String versionId = "MP-" + UUID.randomUUID().toString().substring(0, 8);
        persistResult(versionId, solution, solveDurationMs, resolved, parentPlanVersionId, null);
        persistLineOpenings(versionId);
        return versionId;
    }

    /**
     * 持久化求解器无关 allocation DTO（Session confirm / OntologyStatePersister）。
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public String persistFromAllocations(
            List<MasterPlanAllocationDto> allocations,
            String score,
            long solveDurationMs,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            String parentPlanVersionId) {
        String versionId = "MP-" + UUID.randomUUID().toString().substring(0, 8);
        persistPlanVersionHeader(versionId, score, solveDurationMs, resolved, parentPlanVersionId, null);
        persistAllocationRows(versionId, allocations);
        persistLineOpenings(versionId);
        return versionId;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void persistAllocationRows(String versionId, List<MasterPlanAllocationDto> allocations) {
        if (allocations == null) {
            return;
        }
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.workOrderNo() == null || allocation.workOrderNo().isBlank()) {
                continue;
            }
            MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
            row.planVersionId = versionId;
            row.allocationId = allocation.allocationId();
            row.workOrderNo = allocation.workOrderNo();
            row.productCode = allocation.productCode();
            row.salesOrderNo = allocation.salesOrderNo();
            row.salesOrderLineNo = allocation.salesOrderLineNo();
            row.resourceId = allocation.resourceId();
            row.slotIndex = allocation.slotIndex();
            row.slotDate = allocation.slotDate();
            row.shiftId = allocation.shiftId();
            row.durationMinutes = allocation.durationMinutes();
            row.stampWorkspace();
            row.persist();
        }
    }

    private void persistPlanVersionHeader(
            String versionId,
            String score,
            long durationMs,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            String parentPlanVersionId,
            String sourceDetailScheduleVersionId) {
        PlanVersionEntity version = new PlanVersionEntity();
        version.planVersionId = versionId;
        version.planType = "MASTER_PLAN";
        version.planGeneratedTs = LocalDateTime.now();
        version.changeSource = parentPlanVersionId != null ? "APS_FEEDBACK" : "APS";
        version.solveDurationMs = durationMs;
        version.score = score;
        version.capacityStrategy = resolved.capacityStrategy().name();
        version.strategyId = resolved.id();
        version.strategyName = resolved.name();
        version.parentPlanVersionId = parentPlanVersionId;
        version.sourceDetailScheduleVersionId = sourceDetailScheduleVersionId;
        version.stampWorkspace();
        version.persist();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void persistResult(
            String versionId,
            MasterPlanSchedule solution,
            long durationMs,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            String parentPlanVersionId,
            String sourceDetailScheduleVersionId) {
        persistPlanVersionHeader(
                versionId,
                solution.score() != null ? solution.score().toString() : null,
                durationMs,
                resolved,
                parentPlanVersionId,
                sourceDetailScheduleVersionId);

        if (solution.hasResourceCapacityAssignments()) {
            persistAllocationRows(
                    versionId,
                    ResourceCapacityResultProjector.toAllocationDtos(solution.getResourceCapacityAssignments()));
            return;
        }

        for (OrderAllocation a : solution.getOrderAllocations()) {
            if (a.getTimeSlot() == null) {
                continue;
            }
            MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
            row.planVersionId = versionId;
            row.allocationId = a.getId();
            row.workOrderNo = a.getWorkOrderNo();
            row.productCode = a.getProductCode();
            row.salesOrderNo = a.getSalesOrderNo();
            row.salesOrderLineNo = a.getSalesOrderLineNo();
            row.resourceId = a.getTimeSlot().getResourceId();
            row.slotIndex = a.getTimeSlot().getIndex();
            row.slotDate = a.getTimeSlot().getDate();
            row.shiftId = a.getTimeSlot().getShiftId();
            row.durationMinutes = a.getDurationMinutes();
            row.stampWorkspace();
            row.persist();
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void persistLineOpenings(String versionId) {
        CapacityAnalysisDto capacity = capacityService.analyzeForMasterPlan(versionId);
        for (com.plantops.api.dto.LineOpeningSuggestionDto o : capacity.lineOpeningSuggestions()) {
            LineOpeningDecisionEntity row = new LineOpeningDecisionEntity();
            row.planVersionId = versionId;
            row.areaId = o.areaId();
            row.lineId = o.lineId();
            row.shiftId = o.shiftId();
            row.calendarDate = o.date();
            row.opened = o.open();
            row.suggestedHeadcount = o.suggestedHeadcount();
            row.stampWorkspace();
            row.persist();
        }
    }

    private MasterPlanAllocationDto toAllocationDto(OrderAllocation a) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(a.getWorkOrderNo());
        BigDecimal qty = wo != null ? wo.quantity : BigDecimal.ZERO;
        String parent = wo != null ? wo.parentWorkOrderNo : a.getParentWorkOrderNo();
        String source = wo != null && wo.bomLevel == 0 ? SOURCE_EXTERNAL : SOURCE_REPLENISH;
        LocalDate slotDate = a.getTimeSlot().getDate();
        String shiftId = a.getTimeSlot().getShiftId();
        int duration = a.getDurationMinutes();
        LocalDateTime startTs = shiftStart(slotDate, shiftId);
        LocalDateTime endTs = a.getTimeSlot().isWeekly()
                ? shiftStart(a.getTimeSlot().getPeriodEnd(), shiftId).plusHours(8)
                : startTs.plusMinutes(Math.max(1, duration));
        return new MasterPlanAllocationDto(
                a.getId(),
                a.getSegmentIndex(),
                a.getWorkOrderNo(),
                parent,
                source,
                a.getProductCode(),
                qty,
                a.getSalesOrderNo(),
                a.getSalesOrderLineNo(),
                a.getResourceId(),
                a.getTimeSlot().getIndex(),
                slotDate,
                shiftId,
                startTs,
                endTs,
                duration);
    }

    private MasterPlanAllocationDto toAllocationDto(MasterPlanAllocationEntity entity) {
        WorkOrderEntity wo = WorkOrderEntity.findByNo(entity.workOrderNo);
        BigDecimal qty = wo != null ? wo.quantity : BigDecimal.ZERO;
        String parent = wo != null ? wo.parentWorkOrderNo : null;
        String source = wo != null && wo.bomLevel == 0 ? SOURCE_EXTERNAL : SOURCE_REPLENISH;
        int duration = entity.durationMinutes != null && entity.durationMinutes > 0
                ? entity.durationMinutes
                : (wo != null ? workOrderMinutes(wo) : DEFAULT_SHIFT_MINUTES);
        int segmentIndex = segmentIndexFromAllocationId(entity.allocationId, entity.workOrderNo);
        LocalDateTime startTs = shiftStart(entity.slotDate, entity.shiftId);
        LocalDateTime endTs = TimeslotHorizonService.SHIFT_WEEK.equals(entity.shiftId)
                ? shiftStart(entity.slotDate, entity.shiftId).plusDays(7)
                : startTs.plusMinutes(Math.max(1, duration));
        String allocationId = entity.allocationId != null ? entity.allocationId : entity.workOrderNo;
        return new MasterPlanAllocationDto(
                allocationId,
                segmentIndex,
                entity.workOrderNo,
                parent,
                source,
                entity.productCode,
                qty,
                entity.salesOrderNo,
                entity.salesOrderLineNo,
                entity.resourceId,
                entity.slotIndex,
                entity.slotDate,
                entity.shiftId,
                startTs,
                endTs,
                duration);
    }

    private static int segmentIndexFromAllocationId(String allocationId, String workOrderNo) {
        if (allocationId == null || workOrderNo == null || !allocationId.startsWith(workOrderNo)) {
            return 0;
        }
        int hash = allocationId.lastIndexOf('#');
        if (hash < 0 || hash + 1 >= allocationId.length()) {
            return 0;
        }
        try {
            return Integer.parseInt(allocationId.substring(hash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);

    private static final Pattern ALLOCATION_OP_SEQ =
            Pattern.compile("@OP(\\d+)_\\d+#");

    private static int sumAllocationMinutes(List<MasterPlanAllocationEntity> rows) {
        return rows.stream()
                .mapToInt(a -> a.durationMinutes != null && a.durationMinutes > 0 ? a.durationMinutes : 0)
                .sum();
    }

    private Map<Integer, List<MasterPlanAllocationEntity>> groupAllocationsByOpSeq(
            List<MasterPlanAllocationEntity> rows) {
        Map<Integer, List<MasterPlanAllocationEntity>> map = new LinkedHashMap<>();
        for (MasterPlanAllocationEntity row : rows) {
            Integer seq = parseOpSeqFromAllocationId(row.allocationId);
            if (seq == null) {
                continue;
            }
            map.computeIfAbsent(seq, k -> new ArrayList<>()).add(row);
        }
        for (List<MasterPlanAllocationEntity> list : map.values()) {
            list.sort(Comparator
                    .comparing((MasterPlanAllocationEntity a) -> a.slotDate)
                    .thenComparingInt(a -> a.slotIndex));
        }
        return map;
    }

    private static Integer parseOpSeqFromAllocationId(String allocationId) {
        if (allocationId == null || allocationId.isBlank()) {
            return null;
        }
        Matcher m = ALLOCATION_OP_SEQ.matcher(allocationId);
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<MasterPlanAllocationEntity> filterSegmentsForStep(
            List<MasterPlanAllocationEntity> segments,
            String resourceId) {
        if (segments == null || segments.isEmpty()) {
            return segments;
        }
        List<MasterPlanAllocationEntity> matched = segments.stream()
                .filter(s -> resourceId != null && resourceId.equals(s.resourceId))
                .toList();
        return matched.isEmpty() ? segments : matched;
    }

    /**
     * 工序条与主计划 DAY 槽位对齐：按 allocation_id 中的 @OP{seq} 聚合拆段，起止取首尾槽位日。
     */
    private List<WorkOrderCapacityOperationDto> buildCapacityOperationsFromAllocations(
            String workOrderNo,
            String productCode,
            List<RoutingStep> steps,
            Map<Integer, List<MasterPlanAllocationEntity>> allocsByOpSeq,
            LocalDateTime fallbackStart,
            LocalDateTime fallbackEnd,
            int fallbackTotalMinutes,
            OperationTransferTimeIndex transferRules) {
        List<WorkOrderCapacityOperationDto> operations = new ArrayList<>();
        int stepCount = Math.max(1, steps.size());
        long totalProcessSeconds = 0;
        for (RoutingStep s : steps) {
            totalProcessSeconds += s.processTimeSeconds() != null ? s.processTimeSeconds().longValue() : 0;
        }
        LocalDateTime cursor = fallbackStart;
        for (int i = 0; i < steps.size(); i++) {
            RoutingStep step = steps.get(i);
            int displaySeq = step.sequenceNo() > 0 ? step.sequenceNo() * 10 : (10 + i * 10);
            List<MasterPlanAllocationEntity> segs = filterSegmentsForStep(allocsByOpSeq.get(step.sequenceNo()), step.resourceId());
            LocalDateTime opStart;
            LocalDateTime opEnd;
            int dur;
            if (segs != null && !segs.isEmpty()) {
                MasterPlanAllocationEntity first = segs.get(0);
                MasterPlanAllocationEntity last = segs.get(segs.size() - 1);
                opStart = shiftStart(first.slotDate, first.shiftId);
                int lastMinutes = last.durationMinutes != null && last.durationMinutes > 0
                        ? last.durationMinutes
                        : DEFAULT_SHIFT_MINUTES;
                opEnd = shiftStart(last.slotDate, last.shiftId).plusMinutes(lastMinutes);
                dur = segs.stream()
                        .mapToInt(s -> s.durationMinutes != null && s.durationMinutes > 0 ? s.durationMinutes : 0)
                        .sum();
                if (dur <= 0) {
                    dur = Math.max(1, (int) java.time.Duration.between(opStart, opEnd).toMinutes());
                }
                cursor = opEnd;
            } else {
                if (i == steps.size() - 1) {
                    opEnd = fallbackEnd;
                } else if (totalProcessSeconds > 0 && step.processTimeSeconds() != null) {
                    long share = step.processTimeSeconds().longValue();
                    long minutes = Math.max(15, Math.round((double) fallbackTotalMinutes * share / totalProcessSeconds));
                    opEnd = cursor.plusMinutes(minutes);
                    if (opEnd.isAfter(fallbackEnd)) {
                        opEnd = fallbackEnd;
                    }
                } else {
                    int perOp = Math.max(15, fallbackTotalMinutes / stepCount);
                    opEnd = cursor.plusMinutes(perOp);
                    if (opEnd.isAfter(fallbackEnd)) {
                        opEnd = fallbackEnd;
                    }
                }
                opStart = cursor;
                dur = Math.max(1, (int) java.time.Duration.between(opStart, opEnd).toMinutes());
                cursor = opEnd;
            }
            operations.add(new WorkOrderCapacityOperationDto(
                    workOrderNo + "-OP" + displaySeq,
                    step.operationName(),
                    displaySeq,
                    step.resourceId(),
                    step.allowedResourceIds() != null && !step.allowedResourceIds().isEmpty()
                            ? step.allowedResourceIds()
                            : List.of(step.resourceId()),
                    opStart,
                    opEnd,
                    dur));
            if (i + 1 < steps.size()) {
                RoutingStep nextStep = steps.get(i + 1);
                int transferGap = transferRules.transferMinutes(
                        productCode, step.operationName(), nextStep.operationName());
                if (transferGap > 0 && (segs == null || segs.isEmpty())) {
                    cursor = cursor.plusMinutes(transferGap);
                }
            }
        }
        return operations;
    }

    private List<DemandPoolKpiDto> buildKpis(String score, List<MasterPlanAllocationDto> allocations) {
        List<DemandPoolKpiDto> base = new ArrayList<>();
        if (score != null && !score.isBlank()) {
            Matcher m = HARD_SOFT_SCORE_PATTERN.matcher(score.trim());
            if (m.find()) {
                int hard = Integer.parseInt(m.group("hard"));
                int soft = Integer.parseInt(m.group("soft"));
                base.add(kpi("mp_score_hard", "Score(Hard)", hard, "hard", hard < 0 ? "danger" : "ok"));
                base.add(kpi("mp_score_soft", "Score(Soft)", soft, "soft", soft < 0 ? "warn" : "ok"));
            } else {
                base.add(kpi("mp_score_present", "Score", 1, "str", "info"));
            }
        }

        long total = allocations.stream().map(MasterPlanAllocationDto::workOrderNo).distinct().count();
        long external = allocations.stream()
                .filter(a -> SOURCE_EXTERNAL.equals(a.workOrderSource()))
                .map(MasterPlanAllocationDto::workOrderNo)
                .distinct()
                .count();
        long replenish = total - external;
        long resources = allocations.stream().map(MasterPlanAllocationDto::resourceId).distinct().count();
        int totalMinutes = allocations.stream().mapToInt(MasterPlanAllocationDto::durationMinutes).sum();
        int avgLoadMin = total == 0 ? 0 : totalMinutes / (int) Math.max(1, total);
        LocalDate today = LocalDate.now();
        long latest = allocations.stream()
                .map(a -> horizonEndDay(a))
                .max(Long::compareTo)
                .orElse(0L);

        String externalSeverity = external > 0 ? "info" : "ok";
        String replenishSeverity = replenish > 0 ? "warn" : "ok";

        base.addAll(List.of(
                kpi("mp_total_wo", "已排工单", total, "单", "info"),
                kpi("mp_allocation_rows", "分配条数", allocations.size(), "条", "info"),
                kpi("mp_external", "成品工单", external, "单", externalSeverity),
                kpi("mp_replenish", "组件工单", replenish, "单", replenishSeverity),
                kpi("mp_resources", "占用机台", resources, "台", "info"),
                kpi("mp_total_load", "总负荷", totalMinutes, "分钟", "info"),
                kpi("mp_avg_load", "工单平均负荷", avgLoadMin, "分钟", "info"),
                kpi("mp_horizon_span", "排程跨度", latest, "天", "info")));

        return base;
    }

    private DemandPoolKpiDto kpi(String id, String label, double value, String unit, String severity) {
        return new DemandPoolKpiDto(id, label, value, unit, severity);
    }

    private int workOrderMinutes(WorkOrderEntity wo) {
        return ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
    }

    private long horizonEndDay(MasterPlanAllocationDto a) {
        LocalDate today = LocalDate.now();
        LocalDate end = a.slotDate();
        if (TimeslotHorizonService.SHIFT_WEEK.equals(a.shiftId())) {
            end = a.slotDate().plusDays(6);
        }
        return java.time.temporal.ChronoUnit.DAYS.between(today, end);
    }

    private LocalDateTime shiftStart(LocalDate date, String shiftId) {
        if (TimeslotHorizonService.SHIFT_WEEK.equals(shiftId)) {
            return date.atTime(8, 0);
        }
        int hour = "S2".equals(shiftId) || "NIGHT".equals(shiftId) ? 16 : 8;
        return date.atTime(hour, 0);
    }

    private static LocalDate parseFeedbackCutoff(String feedbackCutoff) {
        if (feedbackCutoff == null || feedbackCutoff.isBlank()) {
            return null;
        }
        return LocalDate.parse(feedbackCutoff);
    }

    public List<MasterPlanAllocationDto> allocationsFromSolution(MasterPlanSchedule solution) {
        if (solution == null) {
            return List.of();
        }
        if (solution.hasResourceCapacityAssignments()) {
            return ResourceCapacityResultProjector.toAllocationDtos(solution.getResourceCapacityAssignments());
        }
        if (solution.getOrderAllocations() == null) {
            return List.of();
        }
        return solution.getOrderAllocations().stream()
                .filter(a -> a.getTimeSlot() != null)
                .map(this::toAllocationDto)
                .toList();
    }

    private MasterPlanPlanningPreviewDto toPreviewDto(
            MasterPlanPlanningContext context,
            MasterPlanStrategyConfigService.ResolvedStrategy resolved,
            LocalDateTime computedAt,
            boolean overlayActive,
            boolean solved,
            boolean persisted,
            String planVersionId,
            String score,
            Long solveDurationMs,
            List<MasterPlanAllocationDto> scheduledAllocations) {
        List<MasterPlanPlanningPreviewAllocationDto> allocations = context.multiResourceSplit()
                ? buildPreviewAllocationsFromResourceCapacity(
                        context.resourceCapacityAssignments(), scheduledAllocations)
                : buildPreviewAllocations(context.orderAllocations(), scheduledAllocations);
        int scheduledCount = (int) allocations.stream()
                .filter(MasterPlanPlanningPreviewAllocationDto::scheduled)
                .count();
        return new MasterPlanPlanningPreviewDto(
                computedAt,
                context.planningStart(),
                resolved.id(),
                resolved.name(),
                resolved.capacityStrategy().name(),
                overlayActive,
                solved,
                persisted,
                planVersionId,
                score,
                solveDurationMs,
                context.diagnostics(),
                allocations,
                allocations.size(),
                scheduledCount);
    }

    private List<MasterPlanPlanningPreviewAllocationDto> buildPreviewAllocations(
            List<OrderAllocation> candidates,
            List<MasterPlanAllocationDto> scheduledAllocations) {
        java.util.Map<String, MasterPlanAllocationDto> scheduledById = new java.util.HashMap<>();
        if (scheduledAllocations != null) {
            for (MasterPlanAllocationDto row : scheduledAllocations) {
                scheduledById.put(row.allocationId(), row);
            }
        }
        if (candidates == null) {
            return List.of();
        }
        List<MasterPlanPlanningPreviewAllocationDto> out = new ArrayList<>(candidates.size());
        for (OrderAllocation a : candidates) {
            MasterPlanAllocationDto scheduled = scheduledById.get(a.getId());
            if (scheduled != null) {
                Integer opSeq = parseOpSeqFromAllocationId(scheduled.allocationId());
                out.add(new MasterPlanPlanningPreviewAllocationDto(
                        scheduled.allocationId(),
                        scheduled.segmentIndex(),
                        scheduled.workOrderNo(),
                        scheduled.productCode(),
                        scheduled.resourceId(),
                        opSeq != null ? opSeq : a.getOperationSeq(),
                        a.getOperationName(),
                        a.getDueDate(),
                        scheduled.durationMinutes(),
                        true,
                        scheduled.slotIndex(),
                        scheduled.slotDate(),
                        scheduled.shiftId(),
                        scheduled.plannedStartTs(),
                        scheduled.plannedEndTs()));
            } else if (a.getTimeSlot() != null) {
                LocalDate slotDate = a.getTimeSlot().getDate();
                String shiftId = a.getTimeSlot().getShiftId();
                int duration = a.getDurationMinutes();
                LocalDateTime startTs = shiftStart(slotDate, shiftId);
                LocalDateTime endTs = a.getTimeSlot().isWeekly()
                        ? shiftStart(a.getTimeSlot().getPeriodEnd(), shiftId).plusHours(8)
                        : startTs.plusMinutes(Math.max(1, duration));
                out.add(new MasterPlanPlanningPreviewAllocationDto(
                        a.getId(),
                        a.getSegmentIndex(),
                        a.getWorkOrderNo(),
                        a.getProductCode(),
                        a.getResourceId(),
                        a.getOperationSeq(),
                        a.getOperationName(),
                        a.getDueDate(),
                        duration,
                        true,
                        a.getTimeSlot().getIndex(),
                        slotDate,
                        shiftId,
                        startTs,
                        endTs));
            } else {
                out.add(new MasterPlanPlanningPreviewAllocationDto(
                        a.getId(),
                        a.getSegmentIndex(),
                        a.getWorkOrderNo(),
                        a.getProductCode(),
                        a.getResourceId(),
                        a.getOperationSeq(),
                        a.getOperationName(),
                        a.getDueDate(),
                        a.getDurationMinutes(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null));
            }
        }
        out.sort(Comparator
                .comparing(MasterPlanPlanningPreviewAllocationDto::workOrderNo, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(MasterPlanPlanningPreviewAllocationDto::operationSeq)
                .thenComparing(MasterPlanPlanningPreviewAllocationDto::allocationId));
        return out;
    }

    private List<MasterPlanPlanningPreviewAllocationDto> buildPreviewAllocationsFromResourceCapacity(
            List<ResourceCapacityAssignment> candidates,
            List<MasterPlanAllocationDto> scheduledAllocations) {
        java.util.Map<String, MasterPlanAllocationDto> scheduledById = new java.util.HashMap<>();
        if (scheduledAllocations != null) {
            for (MasterPlanAllocationDto row : scheduledAllocations) {
                scheduledById.put(row.allocationId(), row);
            }
        }
        if (candidates == null) {
            return List.of();
        }
        List<MasterPlanPlanningPreviewAllocationDto> out = new ArrayList<>(candidates.size());
        for (ResourceCapacityAssignment a : candidates) {
            MasterPlanAllocationDto scheduled = scheduledById.get(a.getId());
            if (scheduled != null) {
                out.add(new MasterPlanPlanningPreviewAllocationDto(
                        scheduled.allocationId(),
                        scheduled.segmentIndex(),
                        scheduled.workOrderNo(),
                        scheduled.productCode(),
                        scheduled.resourceId(),
                        a.getOperationSeq(),
                        a.getOperationName(),
                        a.getDueDate(),
                        scheduled.durationMinutes(),
                        true,
                        scheduled.slotIndex(),
                        scheduled.slotDate(),
                        scheduled.shiftId(),
                        scheduled.plannedStartTs(),
                        scheduled.plannedEndTs()));
            } else {
                out.add(new MasterPlanPlanningPreviewAllocationDto(
                        a.getId(),
                        a.getDaySegmentIndex(),
                        a.getWorkOrderNo(),
                        a.getProductCode(),
                        a.getResourceId(),
                        a.getOperationSeq(),
                        a.getOperationName(),
                        a.getDueDate(),
                        a.getSlotCapacityMinutes(),
                        false,
                        null,
                        null,
                        null,
                        null,
                        null));
            }
        }
        out.sort(Comparator
                .comparing(MasterPlanPlanningPreviewAllocationDto::workOrderNo, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(MasterPlanPlanningPreviewAllocationDto::operationSeq)
                .thenComparing(MasterPlanPlanningPreviewAllocationDto::allocationId));
        return out;
    }
}
