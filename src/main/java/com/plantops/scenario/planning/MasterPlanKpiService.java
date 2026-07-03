package com.plantops.scenario.planning;

import ai.timefold.solver.core.api.score.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.KpiBreakdownDto;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.MasterPlanKpisResponseDto;
import com.plantops.api.dto.planning.PlanningScoreExplanationDto;
import com.plantops.config.SolverRuntimeFactory;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/** §15 KPI-MP-TOT breakdown persistence and KPI-MP-B01~B10 API assembly (TODO-16). */
@ApplicationScoped
public class MasterPlanKpiService {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverRuntimeFactory solverRuntimeFactory;

    @Inject
    PlanningScoreExplainer scoreExplainer;

    @Inject
    MasterPlanBusinessKpiCalculator businessKpiCalculator;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    PlanningScoreExplainService planningScoreExplainService;

    public MasterPlanKpisResponseDto getKpis(String planVersionId) {
        PlanVersionEntity version = requireMasterPlanVersion(planVersionId);
        KpiBreakdownDto breakdown = readBreakdown(version);
        if (breakdown == null) {
            PlanningScoreExplanationDto explanation = tryExplain(planVersionId);
            breakdown = explanation != null
                    ? MasterPlanKpiBreakdownBuilder.fromExplanation(explanation)
                    : MasterPlanKpiBreakdownBuilder.fromScoreString(version.score);
        }
        Integer totalKpi = version.totalKpi != null
                ? version.totalKpi
                : MasterPlanKpiBreakdownBuilder.totalKpiFromScore(version.score);
        PlanningScoreExplanationDto explanation = tryExplain(planVersionId);
        String scoreSummary = MasterPlanKpiBreakdownBuilder.scoreSummary(version.score, explanation);
        var graph = authoritativeOntologyGraph.getOrLoad(
                WorkspaceResolver.currentWorkspaceId(), planVersionId);
        return new MasterPlanKpisResponseDto(
                planVersionId,
                totalKpi,
                scoreSummary,
                breakdown,
                businessKpiCalculator.compute(version, graph));
    }

    public void persistFromSchedule(String planVersionId, MasterPlanSchedule schedule) {
        if (planVersionId == null || planVersionId.isBlank() || schedule == null) {
            return;
        }
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
        if (version == null) {
            return;
        }
        SolutionManager<MasterPlanSchedule, HardSoftScore> manager =
                solverRuntimeFactory.createMasterPlanSolutionManager();
        PlanningScoreExplanationDto explanation =
                scoreExplainer.explainMasterPlan(planVersionId, manager.analyze(schedule));
        persistBreakdown(version, explanation);
    }

    public void persistFromScoreExplanation(PlanVersionEntity version, PlanningScoreExplanationDto explanation) {
        if (version == null) {
            return;
        }
        persistBreakdown(version, explanation);
    }

    public KpiBreakdownDto readBreakdown(PlanVersionEntity version) {
        if (version == null || version.kpiBreakdownJson == null || version.kpiBreakdownJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(version.kpiBreakdownJson, KpiBreakdownDto.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public Integer readTotalKpi(PlanVersionEntity version) {
        if (version == null) {
            return null;
        }
        if (version.totalKpi != null) {
            return version.totalKpi;
        }
        return MasterPlanKpiBreakdownBuilder.totalKpiFromScore(version.score);
    }

    public String readScoreSummary(PlanVersionEntity version) {
        if (version == null) {
            return "N/A";
        }
        PlanningScoreExplanationDto explanation = tryExplain(version.planVersionId);
        return MasterPlanKpiBreakdownBuilder.scoreSummary(version.score, explanation);
    }

    private void persistBreakdown(PlanVersionEntity version, PlanningScoreExplanationDto explanation) {
        KpiBreakdownDto breakdown = explanation != null
                ? MasterPlanKpiBreakdownBuilder.fromExplanation(explanation)
                : MasterPlanKpiBreakdownBuilder.fromScoreString(version.score);
        version.totalKpi = explanation != null
                ? explanation.hardScore() + explanation.softScore()
                : MasterPlanKpiBreakdownBuilder.totalKpiFromScore(version.score);
        try {
            version.kpiBreakdownJson = objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException ex) {
            version.kpiBreakdownJson = null;
        }
        version.persist();
    }

    private PlanningScoreExplanationDto tryExplain(String planVersionId) {
        try {
            return planningScoreExplainService.explainMasterPlan(planVersionId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static PlanVersionEntity requireMasterPlanVersion(String planVersionId) {
        PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
        if (version == null || !"MASTER_PLAN".equals(version.planType)) {
            throw new NotFoundException("Master plan version not found: " + planVersionId);
        }
        return version;
    }
}
