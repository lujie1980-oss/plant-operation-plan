package com.plantops.scenario;

import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.DemandPoolSummaryDto;
import com.plantops.api.dto.PlanningScenarioDto;
import com.plantops.api.dto.ScenarioComparisonDto;
import com.plantops.api.dto.ScenarioComparisonSeriesDto;
import com.plantops.api.dto.ScenarioMetricDto;
import com.plantops.api.dto.planning.MasterPlanKpiDtos.BusinessKpiDto;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.PlanningPipelineRunEntity;
import com.plantops.scenario.planning.MasterPlanBusinessKpiCalculator;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import com.plantops.workspace.WorkspaceResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** VAL-06 multi ENT-PV comparison: Score, COLD delivery, §15 B01~B10, capacity, and scheduling KPIs (TODO-03). */
@ApplicationScoped
public class ScenarioComparisonService {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);

    private static final List<ScenarioMetricDto> METRIC_DEFS = buildMetricDefs();

    @Inject
    CapacityService capacityService;

    @Inject
    PlanningScenarioService planningScenarioService;

    @Inject
    MasterPlanBusinessKpiCalculator businessKpiCalculator;

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    public List<PlanningScenarioDto> listScenarios(int limit) {
        return planningScenarioService.list().stream().limit(Math.max(1, limit)).toList();
    }

    public ScenarioComparisonDto compare(List<String> planVersionIds) {
        if (planVersionIds == null || planVersionIds.isEmpty()) {
            return new ScenarioComparisonDto(METRIC_DEFS, List.of());
        }
        List<ScenarioComparisonSeriesDto> series = new ArrayList<>();
        for (String planVersionId : planVersionIds) {
            PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
            if (version == null) {
                continue;
            }
            PlanningPipelineRunEntity run = PlanningPipelineRunEntity
                    .find("masterPlanVersionId = ?1 order by startedTs desc", planVersionId)
                    .firstResult();
            String capacity = version.capacityStrategy != null ? version.capacityStrategy : "UNCONSTRAINED";
            String label = resolveStrategyName(version, run, capacity) + " · " + version.planVersionId;
            Map<String, Double> values = metricValues(version);
            for (ScenarioMetricDto metric : METRIC_DEFS) {
                Double value = values.get(metric.metricId());
                if (value != null) {
                    series.add(new ScenarioComparisonSeriesDto(planVersionId, label, metric.metricId(), value));
                }
            }
        }
        return new ScenarioComparisonDto(METRIC_DEFS, series);
    }

    private Map<String, Double> metricValues(PlanVersionEntity version) {
        Map<String, Double> out = new LinkedHashMap<>();
        String score = version.score;
        if (score != null && !score.isBlank()) {
            Matcher m = HARD_SOFT_SCORE_PATTERN.matcher(score.trim());
            if (m.find()) {
                out.put("mp_score_hard", (double) Integer.parseInt(m.group("hard")));
                out.put("mp_score_soft", (double) Integer.parseInt(m.group("soft")));
            }
        }
        appendColdDeliveryMetrics(out, version.planVersionId);
        appendBusinessKpis(out, version);
        CapacityAnalysisDto cap = capacityService.analyzeForMasterPlan(version.planVersionId);
        for (DemandPoolKpiDto kpi : cap.kpis()) {
            out.put(kpi.metricId(), kpi.value());
        }
        long woCount = MasterPlanAllocationEntity
                .find("planVersionId", version.planVersionId)
                .<MasterPlanAllocationEntity>list()
                .stream()
                .map(a -> a.workOrderNo)
                .distinct()
                .count();
        int totalLoad = MasterPlanAllocationEntity
                .find("planVersionId", version.planVersionId)
                .<MasterPlanAllocationEntity>list()
                .stream()
                .mapToInt(a -> a.durationMinutes != null ? a.durationMinutes : 0)
                .sum();
        out.put("mp_total_wo", (double) woCount);
        out.put("mp_total_load", (double) totalLoad);
        if (version.solveDurationMs != null) {
            out.put("solve_duration", version.solveDurationMs / 1000.0);
        }
        return out;
    }

    private void appendColdDeliveryMetrics(Map<String, Double> out, String planVersionId) {
        DemandPoolSummaryDto summary = ontologyFulfillmentService.deliverySummary(planVersionId);
        for (DemandPoolKpiDto kpi : summary.kpis()) {
            out.put(coldMetricId(kpi.metricId()), kpi.value());
        }
    }

    private void appendBusinessKpis(Map<String, Double> out, PlanVersionEntity version) {
        var graph = authoritativeOntologyGraph.getOrLoad(
                WorkspaceResolver.currentWorkspaceId(), version.planVersionId);
        for (BusinessKpiDto kpi : businessKpiCalculator.compute(version, graph)) {
            out.put(businessMetricId(kpi.kpiId()), kpi.value());
        }
    }

    static String coldMetricId(String sourceId) {
        return "cold_" + sourceId.toLowerCase();
    }

    static String businessMetricId(String kpiId) {
        if (kpiId == null || !kpiId.startsWith("KPI-MP-B")) {
            return kpiId;
        }
        return "mp_" + kpiId.substring("KPI-MP-".length()).toLowerCase();
    }

    private static List<ScenarioMetricDto> buildMetricDefs() {
        List<ScenarioMetricDto> defs = new ArrayList<>();
        defs.add(new ScenarioMetricDto("mp_score_hard", "Score (Hard)", "hard", "bar"));
        defs.add(new ScenarioMetricDto("mp_score_soft", "Score (Soft)", "soft", "bar"));
        defs.add(new ScenarioMetricDto("cold_total_deliveries", "客户交付数", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_kitting_ok", "齐套 OK", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_shortage", "缺料交付", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_at_risk", "满足风险", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_due_7d", "7 日内交期", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_overdue", "已逾期", "条", "bar"));
        defs.add(new ScenarioMetricDto("cold_total_qty", "总交付量", "件", "bar"));
        defs.add(new ScenarioMetricDto("mp_b01", "计划 OTIF 率", "%", "bar"));
        defs.add(new ScenarioMetricDto("mp_b02", "计划延期订单数", "单", "bar"));
        defs.add(new ScenarioMetricDto("mp_b03", "承诺交期偏差 P95（天）", "天", "bar"));
        defs.add(new ScenarioMetricDto("mp_b04", "瓶颈资源利用率", "%", "bar"));
        defs.add(new ScenarioMetricDto("mp_b05", "超载 period 占比", "%", "bar"));
        defs.add(new ScenarioMetricDto("mp_b06", "制造周期 P95（天）", "天", "bar"));
        defs.add(new ScenarioMetricDto("mp_b07", "工序间等待占比", "%", "bar"));
        defs.add(new ScenarioMetricDto("mp_b08", "物料缺口 period 数", "个", "bar"));
        defs.add(new ScenarioMetricDto("mp_b09", "未排程工序数", "道", "bar"));
        defs.add(new ScenarioMetricDto("mp_b10", "主计划求解耗时", "ms", "bar"));
        defs.add(new ScenarioMetricDto("cap_avg_util", "平均利用率", "%", "bar"));
        defs.add(new ScenarioMetricDto("cap_overload", "超载区间", "个", "bar"));
        defs.add(new ScenarioMetricDto("cap_critical", "高负荷区间", "个", "bar"));
        defs.add(new ScenarioMetricDto("mp_total_wo", "已排工单", "张", "bar"));
        defs.add(new ScenarioMetricDto("mp_total_load", "总负荷", "分钟", "bar"));
        defs.add(new ScenarioMetricDto("solve_duration", "求解耗时", "秒", "bar"));
        return List.copyOf(defs);
    }

    private static String resolveStrategyName(
            PlanVersionEntity v,
            PlanningPipelineRunEntity run,
            String capacity) {
        if (v.strategyName != null && !v.strategyName.isBlank()) {
            return v.strategyName;
        }
        if (run != null && run.strategyName != null && !run.strategyName.isBlank()) {
            return run.strategyName;
        }
        return strategyLabel(capacity);
    }

    private static String strategyLabel(String strategy) {
        try {
            return switch (MasterPlanCapacityStrategy.fromString(strategy)) {
                case UNCONSTRAINED -> "无产能约束";
                case FINITE_CAPACITY -> "产能约束";
            };
        } catch (Exception e) {
            return strategy;
        }
    }
}
