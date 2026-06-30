package com.plantops.scenario;



import com.plantops.api.dto.CapacityAnalysisDto;

import com.plantops.api.dto.DemandPoolKpiDto;

import com.plantops.api.dto.LoadBucketDto;

import com.plantops.config.ParameterRegistry;

import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;

import com.plantops.persistence.entity.PlanVersionEntity;

import com.plantops.workspace.WorkspaceResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;



import java.time.LocalDate;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.List;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



@ApplicationScoped

public class CapacityService {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);



    @Inject

    ParameterRegistry parameters;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    SrpLoadBucketProjector srpLoadBucketProjector;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CapacityAnalysisDto analyze() {
        PlanVersionEntity latestMasterPlan = findLatestPlanVersion("MASTER_PLAN");
        String latestMasterPlanId = latestMasterPlan != null ? latestMasterPlan.planVersionId : null;
        return analyzeForMasterPlan(latestMasterPlanId);
    }

    /** 按指定主计划版本分析产能负荷（SRP 可用/占用 + 工单下钻）。 */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CapacityAnalysisDto analyzeForMasterPlan(String masterPlanVersionId) {
        int threshold = parameters.getInt("capacity_overload_threshold_pct", 110);
        PlanVersionEntity version = masterPlanVersionId != null && !masterPlanVersionId.isBlank()
                ? PlanVersionEntity.findByVersionId(masterPlanVersionId)
                : null;
        String score = version != null ? version.score : null;

        var graph = authoritativeOntologyGraph.getSrpCapacityOrLoad(
                WorkspaceResolver.currentWorkspaceId(), masterPlanVersionId);
        SrpLoadBucketProjector.SrpLoadBucketResult result =
                srpLoadBucketProjector.project(graph, masterPlanVersionId, threshold);

        return new CapacityAnalysisDto(
                buildKpis(result.buckets(), score),
                result.buckets(),
                result.openings(),
                result.horizonStart(),
                result.horizonEnd());
    }



    private List<DemandPoolKpiDto> buildKpis(List<LoadBucketDto> buckets, String score) {

        if (buckets.isEmpty()) {

            List<DemandPoolKpiDto> base = new ArrayList<>();
            base.addAll(scoreKpis(score));
            base.addAll(List.of(

                    kpi("cap_resource_count", "瓶颈机台", 0, "台", "info"),

                    kpi("cap_avg_util", "平均利用率", 0, "%", "info"),

                    kpi("cap_overload", "超载区间", 0, "个", "ok"),

                    kpi("cap_critical", "高负荷区间", 0, "个", "ok")));
            return base;

        }

        long resources = buckets.stream().map(LoadBucketDto::resourceId).distinct().count();

        double avgUtil = buckets.stream().mapToInt(LoadBucketDto::utilizationPct).average().orElse(0);

        long overload = buckets.stream().filter(LoadBucketDto::overloaded).count();

        long critical = buckets.stream().filter(b -> b.utilizationPct() > 90).count();
        int totalFeedbackLocked = buckets.stream().mapToInt(LoadBucketDto::feedbackLockedMinutes).sum();

        String avgSeverity = avgUtil > 100 ? "danger" : avgUtil > 90 ? "warn" : "ok";

        List<DemandPoolKpiDto> base = new ArrayList<>();
        base.addAll(scoreKpis(score));
        base.addAll(List.of(

                kpi("cap_resource_count", "瓶颈机台", resources, "台", "info"),

                kpi("cap_avg_util", "平均利用率", avgUtil, "%", avgSeverity),

                kpi("cap_overload", "超载区间", overload, "个", overload > 0 ? "danger" : "ok"),

                kpi("cap_critical", "高负荷区间(>90%)", critical, "个", critical > 0 ? "warn" : "ok"),
                kpi(
                        "cap_feedback_locked",
                        "排程反馈锁定负荷",
                        totalFeedbackLocked,
                        "分钟",
                        totalFeedbackLocked > 0 ? "info" : "ok")));
        return base;

    }

    private List<DemandPoolKpiDto> scoreKpis(String score) {
        if (score == null || score.isBlank()) {
            return List.of();
        }
        Matcher m = HARD_SOFT_SCORE_PATTERN.matcher(score.trim());
        if (!m.find()) {
            return List.of();
        }
        int hard = Integer.parseInt(m.group("hard"));
        int soft = Integer.parseInt(m.group("soft"));
        return List.of(
                kpi("mp_score_hard", "Score(Hard)", hard, "hard", hard < 0 ? "danger" : "ok"),
                kpi("mp_score_soft", "Score(Soft)", soft, "soft", soft < 0 ? "warn" : "ok"));
    }



    private DemandPoolKpiDto kpi(String id, String label, double value, String unit, String severity) {

        return new DemandPoolKpiDto(id, label, value, unit, severity);

    }



    static String bucketKey(String resourceId, LocalDate date, String shiftId) {

        return resourceId + "|" + date + "|" + shiftId;

    }

    private PlanVersionEntity findLatestPlanVersion(String planType) {
        return PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> planType.equals(v.planType))
                .max(Comparator.comparing(v -> v.planGeneratedTs, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }



}

