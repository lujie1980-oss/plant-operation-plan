package com.plantops.scenario;

import com.plantops.api.dto.*;
import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.domain.RescheduleLevel;
import com.plantops.persistence.entity.PlanningPipelineRunEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.api.dto.masterdata.MasterDataValidationDtos.BlockedSalesOrderLine;
import com.plantops.masterdata.MasterDataValidationService;
import com.plantops.sample.SampleDataLoader;
import com.plantops.scenario.planning.MasterPlanPlanningContext;
import com.plantops.scenario.planning.DetailSchedulePlanningContext;
import com.plantops.scenario.planning.MaterialPlanningContext;
import com.plantops.scenario.planning.MaterialPlanningContextBuilder;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlanningOrchestrator {

    @Inject
    DemandService demandService;

    @Inject
    MaterialFeasibilityService materialFeasibilityService;

    @Inject
    KittingService kittingService;

    @Inject
    CapacityService capacityService;

    @Inject
    WorkOrderGenerationService workOrderGenerationService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    SampleDataLoader sampleDataLoader;

    @Inject
    PipelineRunService pipelineRunService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    MasterDataValidationService validationService;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    MaterialPlanningContextBuilder materialPlanningContextBuilder;

    /** @deprecated 保留兼容；仅执行主计划，不含排程与下发 */
    public PipelineResultDto runFullPipeline() throws Exception {
        return runFullPipeline(MasterPlanCapacityStrategy.UNCONSTRAINED);
    }

    public PipelineResultDto runFullPipeline(MasterPlanCapacityStrategy strategy) throws Exception {
        String runId = pipelineRunService.startRun(strategy);
        return executePipelineRun(runId);
    }

    public PipelineResultDto runFullPipeline(String strategyId) throws Exception {
        String runId = pipelineRunService.startRun(strategyId);
        return executePipelineRun(runId);
    }

    public PipelineResultDto runFullPipeline(String strategyId, PipelineExecuteOptions options) throws Exception {
        return runFullPipeline(strategyId, null, null, options);
    }

    public PipelineResultDto runFullPipeline(
            String strategyId,
            String scenarioId,
            String ruleSetVersionId,
            PipelineExecuteOptions options) throws Exception {
        String runId = pipelineRunService.startRun(strategyId, scenarioId, ruleSetVersionId);
        return executePipelineRun(runId, options);
    }

    /**
     * 主计划运行：需求准备 → MRP 物料可行性 → 产能分析 → 主计划求解（不含详细排程与工单下发）。
     */
    public PipelineResultDto executePipelineRun(String runId) throws Exception {
        return executePipelineRun(runId, PipelineExecuteOptions.masterPlanOnly());
    }

    /**
     * 主计划运行，可选：详细排程 + 排程反馈滚动刷新主计划。
     */
    public PipelineResultDto executePipelineRun(String runId, PipelineExecuteOptions options) throws Exception {
        PlanningPipelineRunEntity run = PlanningPipelineRunEntity.findByRunId(runId);
        if (run == null) {
            throw new NotFoundException("Pipeline run not found: " + runId);
        }
        MasterPlanStrategyConfigService.ResolvedStrategy effective = resolveRunStrategy(run);
        try {
            if (run.scenarioId == null || run.scenarioId.isBlank()) {
                run.scenarioId = planningScenarioService.ensureDefaults().scenarioId;
                run.persist();
            }
            String ruleSetId = run.ruleSetVersionId;
            if (ruleSetId == null || ruleSetId.isBlank()) {
                var scenario = com.plantops.persistence.entity.PlanningScenarioEntity.findByScenarioId(run.scenarioId);
                if (scenario != null) {
                    ruleSetId = scenario.ruleSetVersionId;
                }
            }
            if (ruleSetId != null && !ruleSetId.isBlank()) {
                pipelineRunService.appendLog(runId, "INFO", "应用业务规则版本 " + ruleSetId);
                ruleSetVersionService.applyToWorkspace(ruleSetId);
            }
            pipelineRunService.appendLog(runId, "INFO", "开始主计划运行");
            sampleDataLoader.extendCalendarsToHorizon();
            pipelineRunService.appendLog(runId, "INFO", "已按 planning_horizon_days 补齐资源日历");
            if (demandService.getDemandPool().isEmpty() && SalesOrderLineEntity.countInWorkspace() == 0) {
                pipelineRunService.appendLog(runId, "INFO", "需求池为空，加载演示数据");
                sampleDataLoader.loadDemo();
            }
            pipelineRunService.appendLog(runId, "INFO", "加载需求池");
            List<DemandPoolEntryDto> demand = demandService.getDemandPool();
            pipelineRunService.appendLog(runId, "INFO", "需求池 " + demand.size() + " 条");

            pipelineRunService.appendLog(runId, "INFO", "主数据一致性校验（运行前）");
            var report = validationService.validateAll();
            if (!report.errors().isEmpty()) {
                pipelineRunService.appendLog(
                        runId,
                        "WARN",
                        "发现致命问题 " + report.errors().size()
                                + " 条，将过滤对应销售订单行，不生成工单，不进入主计划");
            }
            if (!report.warnings().isEmpty()) {
                pipelineRunService.appendLog(
                        runId,
                        "INFO",
                        "发现警告 " + report.warnings().size() + " 条（不阻断运行）");
            }
            List<BlockedSalesOrderLine> blocked = report.blockedSalesOrderLines();
            if (!blocked.isEmpty()) {
                Map<String, Long> topReasons = blocked.stream()
                        .collect(Collectors.groupingBy(b -> b.ruleId() + ":" + b.reason(), Collectors.counting()));
                String top3 = topReasons.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(3)
                        .map(e -> e.getKey() + " x" + e.getValue())
                        .collect(Collectors.joining("; "));
                pipelineRunService.appendLog(runId, "WARN", "过滤订单行 " + blocked.size() + " 条，Top: " + top3);
            }

            pipelineRunService.appendLog(runId, "INFO", "删除并重建工单");
            java.util.Set<com.plantops.domain.SalesOrderLineId> blockedIds = blocked.stream()
                    .map(b -> new com.plantops.domain.SalesOrderLineId(b.salesOrderNo(), b.salesOrderLineNo()))
                    .collect(java.util.stream.Collectors.toSet());
            workOrderGenerationService.regenerateForAllOpenOrdersSkipping(blockedIds);

            pipelineRunService.appendLog(runId, "INFO", "构建统一库存快照（S04/S05 共用）");
            MaterialPlanningContext materialPlanning = materialPlanningContextBuilder.build();
            pipelineRunService.appendLog(
                    runId,
                    "INFO",
                    "库存快照 " + materialPlanning.inventorySnapshotId()
                            + "，物料种类 " + materialPlanning.inventory().productCount());

            pipelineRunService.appendLog(runId, "INFO", "MRP 物料可行性（基于库存快照）");
            materialFeasibilityService.prepareContext(materialPlanning.inventory());
            pipelineRunService.appendLog(runId, "INFO", "MRP 快照完成（主计划约束使用）");

            pipelineRunService.appendLog(runId, "INFO", "产能分析（运行前基线）");
            CapacityAnalysisDto capacity = capacityService.analyze();
            pipelineRunService.appendLog(
                    runId,
                    "INFO",
                    "产能基线完成，超载区间 " + capacity.loadBuckets().stream().filter(LoadBucketDto::overloaded).count());

            pipelineRunService.appendLog(
                    runId,
                    "INFO",
                    "S04 推演层构建（策略 " + effective.name()
                            + "，产能 " + effective.capacityStrategy().name() + "）");
            MasterPlanPlanningContext masterPlanContext = masterPlanService.buildPlanningContext(
                    effective, MasterPlanCapacityOverlay.empty(), materialPlanning);
            MasterPlanPlanningDiagnosticsDto masterPlanDiagnostics = masterPlanContext.diagnostics();
            pipelineRunService.appendMasterPlanDiagnostics(runId, masterPlanDiagnostics);

            pipelineRunService.appendLog(runId, "INFO", "S04 Timefold 主计划求解");
            MasterPlanResultDto masterPlan = masterPlanService.solveWithPlanningContext(
                    masterPlanContext, effective, null, null);
            pipelineRunService.appendLog(
                    runId,
                    "INFO",
                    "主计划完成 " + masterPlan.planVersionId() + "，Score " + masterPlan.score());
            DetailScheduleResultDto detailSchedule = null;
            MasterPlanRefreshResultDto masterPlanRefresh = null;
            MasterPlanResultDto effectiveMasterPlan = masterPlan;
            DetailSchedulePlanningDiagnosticsDto detailScheduleDiagnostics = null;

            if (options != null && options.includeDetailSchedule()) {
                pipelineRunService.appendLog(runId, "INFO", "S05 推演层构建");
                DetailSchedulePlanningContext detailContext = detailScheduleService.buildPlanningContext(
                        masterPlan.planVersionId(), materialPlanning);
                detailScheduleDiagnostics = detailContext.diagnostics();
                pipelineRunService.appendDetailScheduleDiagnostics(runId, detailScheduleDiagnostics);

                pipelineRunService.appendLog(runId, "INFO", "S05 Timefold 详细排程求解");
                boolean refresh = options.refreshMasterPlanAfterSchedule();
                detailSchedule = detailScheduleService.solveWithPlanningContext(
                        detailContext,
                        masterPlan.planVersionId(),
                        refresh,
                        LocalDate.now());
                pipelineRunService.appendLog(
                        runId,
                        "INFO",
                        "排程完成 " + detailSchedule.planVersionId()
                                + (refresh ? "，已触发反馈闭环" : ""));
                masterPlanRefresh = detailSchedule.masterPlanRefresh();
                if (masterPlanRefresh != null) {
                    effectiveMasterPlan = masterPlanService.getResult(masterPlanRefresh.newMasterPlanVersionId());
                    pipelineRunService.appendLog(
                            runId,
                            "INFO",
                            "主计划已滚动更新为 " + masterPlanRefresh.newMasterPlanVersionId()
                                    + "（冻结 " + masterPlanRefresh.frozenAllocationRows()
                                    + " 条，重排 " + masterPlanRefresh.replannedAllocationRows() + " 条）");
                }
            } else {
                pipelineRunService.appendLog(
                        runId,
                        "INFO",
                        "请在「生产计划」确认并发布工单后，再到「生产排程」执行排程");
            }

            pipelineRunService.completeSuccess(
                    runId, effectiveMasterPlan, detailSchedule, masterPlanDiagnostics, detailScheduleDiagnostics);
            if (run.scenarioId != null && effectiveMasterPlan != null) {
                planningScenarioService.recordMasterPlanVersion(
                        run.scenarioId, effectiveMasterPlan.planVersionId());
            }
            List<PipelineRunLogLineDto> logs = pipelineRunService.getRun(runId).executionLog();
            return new PipelineResultDto(
                    runId,
                    logs,
                    demand,
                    List.of(),
                    capacity,
                    effectiveMasterPlan,
                    detailSchedule,
                    masterPlanRefresh,
                    null,
                    null);
        } catch (Exception e) {
            pipelineRunService.completeFailure(runId, e.getMessage());
            throw e;
        }
    }

    public RescheduleResultDto reschedule(RescheduleLevel level, Map<String, Object> payload) throws Exception {
        List<String> impacted = new ArrayList<>();
        if (payload != null && payload.containsKey("workOrderNo")) {
            impacted.add(String.valueOf(payload.get("workOrderNo")));
        }
        return switch (level) {
            case R0, R1 -> new RescheduleResultDto(level, null, null, impacted);
            case R2 -> {
                MasterPlanResultDto mp = masterPlanService.getResult(
                        payload != null ? String.valueOf(payload.getOrDefault("masterPlanVersionId", "")) : "");
                String mpId = mp != null ? mp.planVersionId() : null;
                if (mpId == null || mpId.isBlank()) {
                    mp = masterPlanService.solve();
                    mpId = mp.planVersionId();
                }
                DetailScheduleResultDto ds = detailScheduleService.solve(mpId);
                yield new RescheduleResultDto(level, mpId, ds.planVersionId(), impacted);
            }
            case R3 -> {
                materialFeasibilityService.prepareContext();
                MasterPlanResultDto mp = masterPlanService.solve();
                kittingService.compute();
                DetailScheduleResultDto ds = detailScheduleService.solve(mp.planVersionId());
                yield new RescheduleResultDto(level, mp.planVersionId(), ds.planVersionId(), impacted);
            }
        };
    }

    private MasterPlanStrategyConfigService.ResolvedStrategy resolveRunStrategy(PlanningPipelineRunEntity run) {
        if (run.strategyId != null && !run.strategyId.isBlank()) {
            return strategyConfigService.resolve(run.strategyId);
        }
        return strategyConfigService.resolveFromRequest(null, run.capacityStrategy);
    }
}
