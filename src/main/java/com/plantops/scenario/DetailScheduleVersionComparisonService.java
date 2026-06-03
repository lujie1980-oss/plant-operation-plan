package com.plantops.scenario;

import com.plantops.api.dto.ScenarioComparisonDto;
import com.plantops.api.dto.ScenarioComparisonSeriesDto;
import com.plantops.api.dto.ScenarioMetricDto;
import com.plantops.api.dto.planning.DetailScheduleVersionSummaryDto;
import com.plantops.persistence.entity.DetailScheduleOperationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@ApplicationScoped
public class DetailScheduleVersionComparisonService {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final List<ScenarioMetricDto> METRIC_DEFS = List.of(
            new ScenarioMetricDto("ds_score_hard", "Score (Hard)", "hard", "bar"),
            new ScenarioMetricDto("ds_score_soft", "Score (Soft)", "soft", "bar"),
            new ScenarioMetricDto("ds_operations", "已排工序", "道", "bar"),
            new ScenarioMetricDto("ds_work_orders", "涉及工单", "张", "bar"),
            new ScenarioMetricDto("ds_batches", "涉及批次", "个", "bar"),
            new ScenarioMetricDto("ds_lines", "占用产线", "条", "bar"),
            new ScenarioMetricDto("solve_duration", "求解耗时", "秒", "bar"));

    public List<DetailScheduleVersionSummaryDto> listVersions(int limit) {
        int cap = Math.max(1, Math.min(limit, 100));
        return PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> "DETAIL_SCHEDULE".equals(v.planType))
                .sorted(Comparator.comparing(
                        (PlanVersionEntity v) -> v.planGeneratedTs,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap)
                .map(this::toSummary)
                .toList();
    }

    public ScenarioComparisonDto compare(List<String> planVersionIds) {
        if (planVersionIds == null || planVersionIds.isEmpty()) {
            return new ScenarioComparisonDto(METRIC_DEFS, List.of());
        }
        List<ScenarioComparisonSeriesDto> series = new ArrayList<>();
        for (String planVersionId : planVersionIds) {
            PlanVersionEntity version = PlanVersionEntity.findByVersionId(planVersionId);
            if (version == null || !"DETAIL_SCHEDULE".equals(version.planType)) {
                continue;
            }
            String label = formatLabel(version);
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

    private DetailScheduleVersionSummaryDto toSummary(PlanVersionEntity version) {
        List<DetailScheduleOperationEntity> ops = DetailScheduleOperationEntity
                .find("planVersionId", version.planVersionId)
                .<DetailScheduleOperationEntity>list();
        int woCount = (int) ops.stream().map(o -> o.workOrderNo).distinct().count();
        int batchCount = (int) ops.stream()
                .map(o -> o.batchNo)
                .filter(b -> b != null && !b.isBlank())
                .distinct()
                .count();
        int lineCount = (int) ops.stream().map(o -> o.lineId).distinct().count();
        String generatedAt = version.planGeneratedTs != null ? version.planGeneratedTs.format(ISO) : null;
        return new DetailScheduleVersionSummaryDto(
                version.planVersionId,
                generatedAt,
                version.score,
                version.solveDurationMs,
                ops.size(),
                woCount,
                batchCount,
                lineCount);
    }

    private Map<String, Double> metricValues(PlanVersionEntity version) {
        Map<String, Double> out = new LinkedHashMap<>();
        String score = version.score;
        if (score != null && !score.isBlank()) {
            Matcher m = HARD_SOFT_SCORE_PATTERN.matcher(score.trim());
            if (m.find()) {
                out.put("ds_score_hard", (double) Integer.parseInt(m.group("hard")));
                out.put("ds_score_soft", (double) Integer.parseInt(m.group("soft")));
            }
        }
        List<DetailScheduleOperationEntity> ops = DetailScheduleOperationEntity
                .find("planVersionId", version.planVersionId)
                .<DetailScheduleOperationEntity>list();
        out.put("ds_operations", (double) ops.size());
        out.put("ds_work_orders", (double) ops.stream().map(o -> o.workOrderNo).distinct().count());
        out.put("ds_batches", (double) ops.stream()
                .map(o -> o.batchNo)
                .filter(b -> b != null && !b.isBlank())
                .distinct()
                .count());
        out.put("ds_lines", (double) ops.stream().map(o -> o.lineId).distinct().count());
        if (version.solveDurationMs != null) {
            out.put("solve_duration", version.solveDurationMs / 1000.0);
        }
        return out;
    }

    private static String formatLabel(PlanVersionEntity version) {
        String ts = version.planGeneratedTs != null
                ? version.planGeneratedTs.format(DateTimeFormatter.ofPattern("MM/dd HH:mm"))
                : "—";
        return ts + " · " + version.planVersionId;
    }
}
