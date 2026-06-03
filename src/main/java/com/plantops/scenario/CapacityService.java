package com.plantops.scenario;



import com.plantops.api.dto.CapacityAnalysisDto;

import com.plantops.api.dto.CapacityBucketWorkOrderDto;

import com.plantops.api.dto.DemandPoolKpiDto;

import com.plantops.api.dto.LoadBucketDto;

import com.plantops.api.dto.LineOpeningSuggestionDto;

import com.plantops.config.ParameterRegistry;

import com.plantops.persistence.entity.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;



import java.math.BigDecimal;

import java.math.RoundingMode;

import java.time.LocalDate;

import java.util.ArrayList;

import java.util.Comparator;

import java.util.LinkedHashMap;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.stream.Collectors;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



@ApplicationScoped

public class CapacityService {

    private static final Pattern HARD_SOFT_SCORE_PATTERN =
            Pattern.compile("(?<hard>-?\\d+)hard/(?<soft>-?\\d+)soft", Pattern.CASE_INSENSITIVE);



    @Inject

    ParameterRegistry parameters;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CapacityAnalysisDto analyze() {
        PlanVersionEntity latestMasterPlan = findLatestPlanVersion("MASTER_PLAN");
        String latestMasterPlanId = latestMasterPlan != null ? latestMasterPlan.planVersionId : null;
        return analyzeForMasterPlan(latestMasterPlanId);
    }

    /** 按指定主计划版本分析产能负荷（用于场景对比）。 */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public CapacityAnalysisDto analyzeForMasterPlan(String masterPlanVersionId) {
        List<LoadBucketDto> buckets = new ArrayList<>();
        List<LineOpeningSuggestionDto> openings = new ArrayList<>();
        int threshold = parameters.getInt("capacity_overload_threshold_pct", 110);
        PlanVersionEntity version = masterPlanVersionId != null && !masterPlanVersionId.isBlank()
                ? PlanVersionEntity.findByVersionId(masterPlanVersionId)
                : null;
        String score = version != null ? version.score : null;

        LocalDate horizonStart = LocalDate.now();
        List<TimeslotHorizonService.BucketKey> bucketKeys = timeslotHorizonService.bucketKeys(horizonStart);

        for (String resourceId : ProductionResourceEntity.routingResourceIds()) {
            for (TimeslotHorizonService.BucketKey key : bucketKeys) {
                LocalDate date = key.bucketDate();
                String shiftId = key.shiftId();
                int available = key.granularity() == com.plantops.solver.masterplan.TimeslotGranularity.WEEK
                        ? timeslotHorizonService.capacityForRange(resourceId, key.periodStart(), key.periodEnd())
                        : timeslotHorizonService.capacityForDay(resourceId, date);

                String bucketId = bucketKey(resourceId, date, shiftId);

                List<CapacityBucketWorkOrderDto> workOrders = resolveWorkOrdersForBucket(

                        resourceId,

                        date,

                        shiftId,

                        masterPlanVersionId);

                int demand = workOrders.stream().mapToInt(CapacityBucketWorkOrderDto::loadMinutes).sum();
                int feedbackLocked = scheduleFeedbackService.frozenMinutesForCapacityBucket(resourceId, key);

                int utilization = available == 0 ? 0 : (int) (demand * 100L / available);

                boolean overloaded = utilization >= threshold;



                buckets.add(new LoadBucketDto(

                        bucketId,

                        resourceId,

                        resourceId,

                        date,

                        shiftId,

                        demand,

                        feedbackLocked,

                        available,

                        utilization,

                        overloaded,

                        workOrders));



                if (overloaded) {

                    ProductionResourceEntity resource = ProductionResourceEntity.findByResourceId(resourceId);

                    if (resource == null) {

                        continue;

                    }

                    int extraLines = Math.min(2, (demand - available) / 400 + 1);

                    for (ProductionLineEntity line : ProductionLineEntity.findByArea(resource.areaId)) {

                        if (extraLines <= 0) {

                            break;

                        }

                        openings.add(new LineOpeningSuggestionDto(

                                resource.areaId,

                                line.lineId,

                                shiftId,

                                date,

                                true,

                                line.lineMinHeadcount,

                                "Heuristic: capacity overload " + utilization + "%"));

                        extraLines--;

                    }

                }

            }

        }



        return new CapacityAnalysisDto(buildKpis(buckets, score), buckets, openings);
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



    private List<CapacityBucketWorkOrderDto> resolveWorkOrdersForBucket(

            String resourceId,

            LocalDate date,

            String shiftId,

            String masterPlanVersionId) {

        Map<String, CapacityBucketWorkOrderDto> byWo = new LinkedHashMap<>();
        if (masterPlanVersionId != null) {
            List<MasterPlanAllocationEntity> allocRows;
            if (TimeslotHorizonService.SHIFT_WEEK.equals(shiftId)) {
                allocRows = MasterPlanAllocationEntity
                        .find("planVersionId = ?1 and resourceId = ?2 and slotDate = ?3 and shiftId = ?4",
                                masterPlanVersionId, resourceId, date, shiftId)
                        .list();
            } else {
                allocRows = MasterPlanAllocationEntity
                        .find("planVersionId = ?1 and resourceId = ?2 and slotDate = ?3 and shiftId = ?4",
                                masterPlanVersionId, resourceId, date, shiftId)
                        .list();
            }
            for (MasterPlanAllocationEntity alloc : allocRows) {

                WorkOrderEntity wo = alloc.workOrderNo != null

                        ? WorkOrderEntity.findByNo(alloc.workOrderNo)

                        : WorkOrderEntity.findRootForOrderLine(

                                alloc.salesOrderNo, alloc.salesOrderLineNo, findProductForLine(alloc));

                if (wo == null) {

                    continue;

                }

                int minutes = alloc.durationMinutes != null && alloc.durationMinutes > 0
                        ? alloc.durationMinutes
                        : workOrderMinutes(wo);

                String rowKey = alloc.allocationId != null ? alloc.allocationId : wo.workOrderNo;

                boolean feedbackLocked = alloc.allocationId != null && alloc.allocationId.startsWith("FB-");
                SalesOrderRef orderRef = resolveSalesOrderRef(wo, alloc);
                byWo.putIfAbsent(rowKey, new CapacityBucketWorkOrderDto(

                        wo.workOrderNo,

                        orderRef.salesOrderNo(),

                        orderRef.salesOrderLineNo(),

                        wo.productCode,

                        wo.quantity,

                        minutes,

                        "主计划",

                        feedbackLocked));

            }

        }



        // 已有主计划版本时，空桶表示该日该机台无排产，不应再回退到需求测算
        if (byWo.isEmpty() && masterPlanVersionId == null) {

            for (SalesOrderLineEntity order : SalesOrderLineEntity.listInWorkspace()) {

                if (!order.dueDate.equals(date)) {

                    continue;

                }

                boolean canProduce = ProductResourceEntity.listInWorkspace().stream()

                        .anyMatch(pr -> pr.productCode.equals(order.productCode) && pr.resourceId.equals(resourceId));

                if (!canProduce) {

                    continue;

                }

                WorkOrderEntity wo = WorkOrderEntity.findRootForOrderLine(

                        order.salesOrderNo, order.salesOrderLineNo, order.productCode);

                if (wo == null || !resourceId.equals(wo.resourceId)) {

                    continue;

                }

                int minutes = workOrderMinutes(wo);

                SalesOrderRef orderRef = resolveSalesOrderRef(wo, null);
                byWo.putIfAbsent(

                        wo.workOrderNo,

                        new CapacityBucketWorkOrderDto(

                                wo.workOrderNo,

                                orderRef.salesOrderNo(),

                                orderRef.salesOrderLineNo(),

                                wo.productCode,

                                wo.quantity,

                                minutes,

                                "需求测算"));

            }

        }



        return byWo.values().stream()

                .sorted(Comparator.comparing(CapacityBucketWorkOrderDto::workOrderNo))

                .toList();

    }



    private String findProductForLine(MasterPlanAllocationEntity alloc) {

        SalesOrderLineEntity line = SalesOrderLineEntity.findByKey(alloc.salesOrderNo, alloc.salesOrderLineNo);

        return line != null ? line.productCode : "";

    }



    private int workOrderMinutes(WorkOrderEntity wo) {
        return ProductRoutingSteps.totalDurationMinutes(wo.productCode, wo.quantity);
    }

    /** 成品工单直接带订单行；组件工单从主计划分配或 pegging 解析。 */
    private static SalesOrderRef resolveSalesOrderRef(WorkOrderEntity wo, MasterPlanAllocationEntity alloc) {
        if (wo.salesOrderNo != null && !wo.salesOrderNo.isBlank()) {
            return new SalesOrderRef(wo.salesOrderNo, wo.salesOrderLineNo);
        }
        if (alloc != null && alloc.salesOrderNo != null && !alloc.salesOrderNo.isBlank()) {
            return new SalesOrderRef(alloc.salesOrderNo, alloc.salesOrderLineNo);
        }
        WorkOrderScheduleContext ctx = WorkOrderScheduleContext.resolve(wo);
        if (ctx.salesOrderNo != null && !ctx.salesOrderNo.isBlank()) {
            return new SalesOrderRef(ctx.salesOrderNo, ctx.salesOrderLineNo);
        }
        return new SalesOrderRef(null, 0);
    }

    private record SalesOrderRef(String salesOrderNo, int salesOrderLineNo) {
    }

    private PlanVersionEntity findLatestPlanVersion(String planType) {
        return PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> planType.equals(v.planType))
                .max(Comparator.comparing(v -> v.planGeneratedTs, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }



}

