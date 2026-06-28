package com.plantops.api;

import com.plantops.api.dto.*;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewDto;
import com.plantops.api.dto.planning.DetailSchedulePlanningPreviewRequest;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewDto;
import com.plantops.api.dto.planning.MasterPlanPlanningPreviewRequest;
import com.plantops.api.dto.planning.DetailScheduleVersionSummaryDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainPreviewRequest;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.scenario.planning.OrderPlanningChainService;
import com.plantops.config.MasterPlanStrategyConfigService;
import com.plantops.scenario.*;
import com.plantops.scenario.planning.PlanningScoreExplainService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class PlanningResource {

    @Inject
    KittingService kittingService;

    @Inject
    CapacityService capacityService;

    @Inject
    StandardResourcePeriodGanttService srpCapacityGanttService;

    @Inject
    MasterPlanService masterPlanService;

    @Inject
    DetailScheduleService detailScheduleService;

    @Inject
    ExecutionService executionService;

    @Inject
    KpiService kpiService;

    @Inject
    PlanningOrchestrator orchestrator;

    @Inject
    PlanVersionService planVersionService;

    @Inject
    PipelineRunService pipelineRunService;

    @Inject
    ScenarioComparisonService scenarioComparisonService;

    @Inject
    DetailScheduleVersionComparisonService detailScheduleVersionComparisonService;

    @Inject
    DetailScheduleKpiService detailScheduleKpiService;

    @Inject
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    PlanningScoreExplainService planningScoreExplainService;

    @Inject
    OrderPlanningChainService orderPlanningChainService;

    @POST
    @Path("/kitting/compute")
    public java.util.List<KittingResultDto> computeKitting() {
        return kittingService.compute();
    }

    @POST
    @Path("/capacity/analyze")
    public CapacityAnalysisDto analyzeCapacity(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        if (masterPlanVersionId != null && !masterPlanVersionId.isBlank()) {
            return capacityService.analyzeForMasterPlan(masterPlanVersionId);
        }
        return capacityService.analyze();
    }

    @GET
    @Path("/capacity/srp-gantt")
    public SrpCapacityGanttDto srpCapacityGantt(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return srpCapacityGanttService.buildForMasterPlan(masterPlanVersionId);
    }

    @POST
    @Path("/planning/master-plan/solve")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    public Response solveMasterPlan(
            @QueryParam("strategyId") String strategyIdQuery,
            @QueryParam("capacityStrategy") String capacityStrategyQuery,
            MasterPlanSolveRequest request) throws Exception {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = resolveStrategy(
                strategyIdQuery,
                capacityStrategyQuery,
                request != null ? request.strategyId() : null,
                request != null ? request.capacityStrategy() : null);
        return Response.ok(masterPlanService.solveWithStrategy(resolved.id())).build();
    }

    @GET
    @Path("/planning/master-plan/result/{versionId}")
    public Response getMasterPlan(@PathParam("versionId") String versionId) {
        MasterPlanResultDto result = masterPlanService.getResult(versionId);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(result).build();
    }

    @GET
    @Path("/planning/master-plan/{versionId}/work-orders/{workOrderNo}/capacity-gantt")
    public Response getWorkOrderCapacityGantt(
            @PathParam("versionId") String versionId,
            @PathParam("workOrderNo") String workOrderNo) {
        return Response.ok(masterPlanService.getWorkOrderCapacityGantt(versionId, workOrderNo)).build();
    }

    /**
     * 主计划推演层统一预览：默认仅 P0–P4；{@code solve} 可选内存/持久化选优，结果反写到分配快照。
     */
    @POST
    @Path("/planning/master-plan/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response previewMasterPlanPlanning(MasterPlanPlanningPreviewRequest request) throws Exception {
        return Response.ok(masterPlanService.previewPlanning(request)).build();
    }

    /**
     * 细排程推演层统一预览：默认仅 P0–P4；{@code solve} 可选内存/持久化选优，结果反写到工序快照。
     */
    @POST
    @Path("/planning/detail-schedule/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response previewDetailSchedulePlanning(DetailSchedulePlanningPreviewRequest request) throws Exception {
        return Response.ok(detailScheduleService.previewPlanning(request)).build();
    }

    /** @deprecated 使用 {@code GET /api/v1/ontology/fulfillment/deliveries/{id}/promise-date-preview} 或 Sandbox optimize。 */
    @Deprecated(since = "1.0", forRemoval = true)
    @POST
    @Path("/planning/order-chain/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    public OrderPlanningChainDto previewOrderPlanningChain(OrderPlanningChainPreviewRequest request) {
        return orderPlanningChainService.preview(request);
    }

    @GET
    @Path("/planning/master-plan/{versionId}/score-explanation")
    public PlanningScoreExplanationDto explainMasterPlanScore(@PathParam("versionId") String versionId) {
        return planningScoreExplainService.explainMasterPlan(versionId);
    }

    @GET
    @Path("/planning/detail-schedule/{versionId}")
    public com.plantops.api.dto.DetailScheduleResultDto getDetailSchedule(
            @PathParam("versionId") String versionId) {
        return detailScheduleService.get(versionId);
    }

    @GET
    @Path("/planning/detail-schedule/page-kpis")
    public java.util.List<DemandPoolKpiDto> detailSchedulePageKpis(
            @QueryParam("detailScheduleVersionId") String detailScheduleVersionId) {
        return detailScheduleKpiService.pageKpis(
                detailScheduleVersionId, resolveDetailScheduleOperations(detailScheduleVersionId));
    }

    @POST
    @Path("/planning/detail-schedule/page-kpis")
    @Consumes(MediaType.APPLICATION_JSON)
    public java.util.List<DemandPoolKpiDto> detailSchedulePageKpisPost(DetailSchedulePageKpisRequestDto request) {
        String versionId = request != null ? request.detailScheduleVersionId() : null;
        java.util.List<DetailScheduleOperationDto> operations =
                request != null && request.operations() != null && !request.operations().isEmpty()
                        ? request.operations()
                        : resolveDetailScheduleOperations(versionId);
        return detailScheduleKpiService.pageKpis(versionId, operations);
    }

    private java.util.List<DetailScheduleOperationDto> resolveDetailScheduleOperations(String detailScheduleVersionId) {
        if (detailScheduleVersionId == null || detailScheduleVersionId.isBlank()) {
            return java.util.List.of();
        }
        try {
            return detailScheduleService.get(detailScheduleVersionId).operations();
        } catch (jakarta.ws.rs.NotFoundException ignored) {
            return java.util.List.of();
        }
    }

    @GET
    @Path("/planning/detail-schedule/{versionId}/score-explanation")
    public PlanningScoreExplanationDto explainDetailScheduleScore(
            @PathParam("versionId") String versionId,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return planningScoreExplainService.explainDetailSchedule(versionId, masterPlanVersionId);
    }

    @POST
    @Path("/planning/detail-schedule/solve")
    public Response solveDetailSchedule(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId,
            @QueryParam("refreshMasterPlan") @DefaultValue("false") boolean refreshMasterPlan,
            @QueryParam("feedbackCutoff") String feedbackCutoff) throws Exception {
        java.time.LocalDate cutoff = feedbackCutoff != null && !feedbackCutoff.isBlank()
                ? java.time.LocalDate.parse(feedbackCutoff)
                : null;
        return Response.ok(detailScheduleService.solve(masterPlanVersionId, refreshMasterPlan, cutoff)).build();
    }

    @POST
    @Path("/planning/schedule-feedback/apply-from-detail-schedule/{detailScheduleVersionId}")
    public Response applyScheduleFeedback(
            @PathParam("detailScheduleVersionId") String detailScheduleVersionId,
            @QueryParam("masterPlanVersionId") String masterPlanVersionId,
            @QueryParam("feedbackCutoff") String feedbackCutoff) {
        java.time.LocalDate cutoff = feedbackCutoff != null && !feedbackCutoff.isBlank()
                ? java.time.LocalDate.parse(feedbackCutoff)
                : java.time.LocalDate.now();
        return Response.ok(scheduleFeedbackService.recordFromDetailSchedule(
                detailScheduleVersionId, masterPlanVersionId, cutoff)).build();
    }

    @GET
    @Path("/planning/schedule-feedback")
    public java.util.List<ScheduleFeedbackDto> listScheduleFeedback(
            @QueryParam("detailScheduleVersionId") String detailScheduleVersionId,
            @QueryParam("frozenThrough") String frozenThrough) {
        if (detailScheduleVersionId != null && !detailScheduleVersionId.isBlank()) {
            return scheduleFeedbackService.listForDetailSchedule(detailScheduleVersionId);
        }
        java.time.LocalDate cutoff = frozenThrough != null && !frozenThrough.isBlank()
                ? java.time.LocalDate.parse(frozenThrough)
                : java.time.LocalDate.now();
        return scheduleFeedbackService.listFrozenUpTo(cutoff);
    }

    @POST
    @Path("/planning/master-plan/refresh-subsequent")
    public Response refreshSubsequentMasterPlan(
            @QueryParam("parentMasterPlanVersionId") String parentMasterPlanVersionId,
            @QueryParam("detailScheduleVersionId") String detailScheduleVersionId,
            @QueryParam("feedbackCutoff") String feedbackCutoff,
            @QueryParam("strategyId") String strategyId) throws Exception {
        java.time.LocalDate cutoff = feedbackCutoff != null && !feedbackCutoff.isBlank()
                ? java.time.LocalDate.parse(feedbackCutoff)
                : java.time.LocalDate.now();
        return Response.ok(masterPlanService.refreshSubsequentPlan(
                parentMasterPlanVersionId,
                detailScheduleVersionId,
                cutoff,
                strategyId)).build();
    }

    @POST
    @Path("/planning/dispatch")
    @Consumes(MediaType.APPLICATION_JSON)
    public DispatchResultDto dispatch(DispatchRequest request) {
        return executionService.dispatch(request.planVersionId());
    }

    @POST
    @Path("/events")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleEvent(PlanningEventDto event) throws Exception {
        return Response.ok(executionService.handleEvent(event)).build();
    }

    @POST
    @Path("/planning/reschedule")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response reschedule(RescheduleRequest request) throws Exception {
        PlanningEventDto event = new PlanningEventDto(
                null,
                request.eventType(),
                null,
                request.payload());
        return Response.ok(executionService.handleEvent(event)).build();
    }

    @GET
    @Path("/kpi/report")
    public KpiReportDto kpiReport() {
        return kpiService.report();
    }

    @GET
    @Path("/planning/pipeline-runs")
    public java.util.List<PlanningPipelineRunDto> listPipelineRuns(
            @QueryParam("limit") @DefaultValue("30") int limit) {
        return pipelineRunService.listRecent(limit);
    }

    @GET
    @Path("/planning/pipeline-runs/{runId}")
    public Response getPipelineRun(@PathParam("runId") String runId) {
        PlanningPipelineRunDto dto = pipelineRunService.getRun(runId);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }

    @POST
    @Path("/planning/pipeline-runs")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    public Response startPipelineRun(
            @QueryParam("strategyId") String strategyIdQuery,
            @QueryParam("capacityStrategy") String capacityStrategyQuery,
            PipelineRunRequest request) {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = resolveStrategy(
                strategyIdQuery,
                capacityStrategyQuery,
                request != null ? request.strategyId() : null,
                request != null ? request.capacityStrategy() : null);
        String scenarioId = request != null ? request.scenarioId() : null;
        String ruleSetVersionId = request != null ? request.ruleSetVersionId() : null;
        String runId = pipelineRunService.startRun(resolved, scenarioId, ruleSetVersionId);
        PlanningPipelineRunDto dto = pipelineRunService.getRun(runId);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @POST
    @Path("/planning/pipeline-runs/{runId}/execute")
    public Response executePipelineRun(
            @PathParam("runId") String runId,
            @QueryParam("includeDetailSchedule") @DefaultValue("false") boolean includeDetailSchedule,
            @QueryParam("refreshMasterPlanAfterSchedule") @DefaultValue("false") boolean refreshMasterPlanAfterSchedule,
            PipelineRunRequest request) throws Exception {
        boolean include = includeDetailSchedule
                || Boolean.TRUE.equals(request != null ? request.includeDetailSchedule() : null);
        boolean refresh = refreshMasterPlanAfterSchedule
                || Boolean.TRUE.equals(request != null ? request.refreshMasterPlanAfterSchedule() : null);
        return Response.ok(orchestrator.executePipelineRun(
                runId,
                com.plantops.scenario.PipelineExecuteOptions.fromRequest(include, refresh))).build();
    }

    @POST
    @Path("/planning/run-full-pipeline")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    public Response runFullPipeline(
            @QueryParam("strategyId") String strategyIdQuery,
            @QueryParam("capacityStrategy") String capacityStrategyQuery,
            @QueryParam("includeDetailSchedule") @DefaultValue("false") boolean includeDetailSchedule,
            @QueryParam("refreshMasterPlanAfterSchedule") @DefaultValue("false") boolean refreshMasterPlanAfterSchedule,
            PipelineRunRequest request) throws Exception {
        MasterPlanStrategyConfigService.ResolvedStrategy resolved = resolveStrategy(
                strategyIdQuery,
                capacityStrategyQuery,
                request != null ? request.strategyId() : null,
                request != null ? request.capacityStrategy() : null);
        boolean include = includeDetailSchedule
                || Boolean.TRUE.equals(request != null ? request.includeDetailSchedule() : null);
        boolean refresh = refreshMasterPlanAfterSchedule
                || Boolean.TRUE.equals(request != null ? request.refreshMasterPlanAfterSchedule() : null);
        String scenarioId = request != null ? request.scenarioId() : null;
        String ruleSetVersionId = request != null ? request.ruleSetVersionId() : null;
        return Response.ok(orchestrator.runFullPipeline(
                resolved.id(),
                scenarioId,
                ruleSetVersionId,
                com.plantops.scenario.PipelineExecuteOptions.fromRequest(include, refresh))).build();
    }

    @GET
    @Path("/planning/scenario-catalog")
    public java.util.List<PlanningScenarioDto> listScenarioCatalog() {
        return planningScenarioService.list();
    }

    @POST
    @Path("/planning/scenario-catalog")
    @Consumes(MediaType.APPLICATION_JSON)
    public PlanningScenarioDto createScenario(CreatePlanningScenarioRequest request) {
        return planningScenarioService.create(request);
    }

    @GET
    @Path("/planning/rule-set-versions")
    public java.util.List<RuleSetVersionDto> listRuleSetVersions() {
        return ruleSetVersionService.list();
    }

    @POST
    @Path("/planning/rule-set-versions")
    @Consumes(MediaType.APPLICATION_JSON)
    public RuleSetVersionDto createRuleSetVersion(CreateRuleSetVersionRequest request) {
        return ruleSetVersionService.create(request);
    }

    @POST
    @Path("/planning/rule-set-versions/{id}/sync-from-workspace")
    public RuleSetVersionDto syncRuleSetFromWorkspace(@PathParam("id") String id) {
        return ruleSetVersionService.syncFromWorkspace(id);
    }

    @GET
    @Path("/planning/compare")
    public PlanVersionCompareDto compare(
            @QueryParam("from") String from,
            @QueryParam("to") String to) {
        return planVersionService.compare(from, to);
    }

    @GET
    @Path("/planning/scenarios")
    public java.util.List<PlanningScenarioDto> listScenarios(
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return scenarioComparisonService.listScenarios(limit);
    }

    @POST
    @Path("/planning/scenarios/compare")
    @Consumes(MediaType.APPLICATION_JSON)
    public ScenarioComparisonDto compareScenarios(ScenarioCompareRequest request) {
        java.util.List<String> ids = request != null && request.planVersionIds() != null
                ? request.planVersionIds()
                : java.util.List.of();
        return scenarioComparisonService.compare(ids);
    }

    @GET
    @Path("/planning/detail-schedule/versions")
    public java.util.List<DetailScheduleVersionSummaryDto> listDetailScheduleVersions(
            @QueryParam("limit") @DefaultValue("50") int limit) {
        return detailScheduleVersionComparisonService.listVersions(limit);
    }

    @POST
    @Path("/planning/detail-schedule/versions/compare")
    @Consumes(MediaType.APPLICATION_JSON)
    public ScenarioComparisonDto compareDetailScheduleVersions(ScenarioCompareRequest request) {
        java.util.List<String> ids = request != null && request.planVersionIds() != null
                ? request.planVersionIds()
                : java.util.List.of();
        return detailScheduleVersionComparisonService.compare(ids);
    }

    public record ScenarioCompareRequest(java.util.List<String> planVersionIds) {
    }

    public record DispatchRequest(String planVersionId) {
    }

    public record RescheduleRequest(String eventType, Map<String, Object> payload) {
    }

    private MasterPlanStrategyConfigService.ResolvedStrategy resolveStrategy(
            String strategyIdQuery,
            String capacityStrategyQuery,
            String strategyIdBody,
            String capacityStrategyBody) {
        String strategyId = strategyIdBody != null && !strategyIdBody.isBlank()
                ? strategyIdBody
                : strategyIdQuery;
        String capacity = capacityStrategyBody != null && !capacityStrategyBody.isBlank()
                ? capacityStrategyBody
                : capacityStrategyQuery;
        return strategyConfigService.resolveFromRequest(strategyId, capacity);
    }
}
