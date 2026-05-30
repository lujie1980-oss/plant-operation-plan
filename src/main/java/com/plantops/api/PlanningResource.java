package com.plantops.api;

import com.plantops.api.dto.*;
import com.plantops.api.dto.planning.DetailSchedulePlanningDiagnosticsDto;
import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
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
    MasterPlanStrategyConfigService strategyConfigService;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    RuleSetVersionService ruleSetVersionService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Inject
    PlanningScoreExplainService planningScoreExplainService;

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

    @GET
    @Path("/planning/master-plan/diagnostics/preview")
    public MasterPlanPlanningDiagnosticsDto previewMasterPlanDiagnostics(
            @QueryParam("strategyId") String strategyId,
            @QueryParam("feedbackCutoff") String feedbackCutoff) {
        java.time.LocalDate cutoff = feedbackCutoff != null && !feedbackCutoff.isBlank()
                ? java.time.LocalDate.parse(feedbackCutoff)
                : null;
        return masterPlanService.previewPlanningDiagnostics(strategyId, cutoff);
    }

    @GET
    @Path("/planning/detail-schedule/diagnostics/preview")
    public DetailSchedulePlanningDiagnosticsDto previewDetailScheduleDiagnostics(
            @QueryParam("masterPlanVersionId") String masterPlanVersionId) {
        return detailScheduleService.previewPlanningDiagnostics(masterPlanVersionId);
    }

    @GET
    @Path("/planning/master-plan/{versionId}/score-explanation")
    public PlanningScoreExplanationDto explainMasterPlanScore(@PathParam("versionId") String versionId) {
        return planningScoreExplainService.explainMasterPlan(versionId);
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
