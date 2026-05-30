package com.plantops.scenario;

import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.DemandPoolKpiDto;
import com.plantops.api.dto.PlanningScenarioDto;
import com.plantops.api.dto.ScenarioComparisonDto;
import com.plantops.api.dto.ScenarioComparisonSeriesDto;
import com.plantops.api.dto.ScenarioMetricDto;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.PlanningPipelineRunEntity;
import com.plantops.solver.masterplan.MasterPlanCapacityStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ScenarioComparisonService {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);

    private static final List<ScenarioMetricDto> METRIC_DEFS = List.of(
            new ScenarioMetricDto("mp_score_hard", "Score (Hard)", "hard", "bar"),
            new ScenarioMetricDto("mp_score_soft", "Score (Soft)", "soft", "bar"),
            new ScenarioMetricDto("cap_avg_util", "平均利用率", "%", "bar"),
            new ScenarioMetricDto("cap_overload", "超载区间", "个", "bar"),
            new ScenarioMetricDto("cap_critical", "高负荷区间", "个", "bar"),
            new ScenarioMetricDto("mp_total_wo", "已排工单", "张", "bar"),
            new ScenarioMetricDto("mp_total_load", "总负荷", "分钟", "bar"),
            new ScenarioMetricDto("solve_duration", "求解耗时", "秒", "bar"));

    @Inject
    CapacityService capacityService;

    @Inject
    PlanningScenarioService planningScenarioService;

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
