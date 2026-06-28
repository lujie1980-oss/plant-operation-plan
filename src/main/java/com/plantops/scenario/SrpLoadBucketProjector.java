package com.plantops.scenario;

import com.plantops.api.dto.CapacityBucketWorkOrderDto;
import com.plantops.api.dto.LoadBucketDto;
import com.plantops.api.dto.LineOpeningSuggestionDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.solver.masterplan.TimeslotGranularity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从本体 {@link StandardResourcePeriod} 投影统一产能分析页的 {@link LoadBucketDto}，
 * 并保留工单下钻与排程反馈锁定分钟数。
 */
@ApplicationScoped
public class SrpLoadBucketProjector {

    private static final String SHIFT_DAY = "DAY";

    @Inject
    CapacityBucketWorkOrderResolver workOrderResolver;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    public SrpLoadBucketResult project(OntologyGraph graph, String masterPlanVersionId, int overloadThresholdPct) {
        List<Period> periods = graph.periodsOrdered();
        if (periods.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new SrpLoadBucketResult(List.of(), List.of(), today, today);
        }

        LocalDate horizonStart = periods.get(0).getStartDate();
        LocalDate horizonEnd = periods.get(periods.size() - 1).getEndDate();
        Map<String, LoadBucketDto> byBucketKey = new LinkedHashMap<>();
        List<LineOpeningSuggestionDto> openings = new ArrayList<>();

        for (StandardResourcePeriod srp : graph.srpById().values()) {
            Period period = StandardResourcePeriodGanttService.periodFor(graph, srp.getPeriodId());
            if (period == null) {
                continue;
            }
            String resourceId = srp.getStandardResourceId();
            String resourceLabel = resolveResourceLabel(resourceId);
            int periodDays = (int) ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
            boolean singleDayPeriod = periodDays == 1;

            for (LocalDate date = period.getStartDate();
                    !date.isAfter(period.getEndDate());
                    date = date.plusDays(1)) {
                TimeslotHorizonService.BucketKey key = new TimeslotHorizonService.BucketKey(
                        date, SHIFT_DAY, date, date, TimeslotGranularity.DAY);
                List<CapacityBucketWorkOrderDto> workOrders =
                        workOrderResolver.resolve(resourceId, date, SHIFT_DAY, masterPlanVersionId);

                int demand = workOrders.stream().mapToInt(CapacityBucketWorkOrderDto::loadMinutes).sum();
                int available = singleDayPeriod
                        ? (int) Math.round(Math.max(0, srp.getAvailableCapacity()))
                        : timeslotHorizonService.capacityForDay(resourceId, date);
                if (singleDayPeriod && demand == 0 && srp.getReservedCapacity() > 0) {
                    demand = (int) Math.round(srp.getReservedCapacity());
                }

                int feedbackLocked = scheduleFeedbackService.frozenMinutesForCapacityBucket(resourceId, key);
                int utilization = available == 0 ? (demand > 0 ? 100 : 0) : (int) (demand * 100L / available);
                boolean overloaded = utilization >= overloadThresholdPct;
                String bucketId = CapacityService.bucketKey(resourceId, date, SHIFT_DAY);

                byBucketKey.put(
                        bucketId,
                        new LoadBucketDto(
                                bucketId,
                                resourceId,
                                resourceLabel,
                                date,
                                SHIFT_DAY,
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
                                SHIFT_DAY,
                                date,
                                true,
                                line.lineMinHeadcount,
                                "Heuristic: capacity overload " + utilization + "%"));
                        extraLines--;
                    }
                }
            }
        }

        List<LoadBucketDto> buckets = byBucketKey.values().stream()
                .sorted(Comparator
                        .comparing(LoadBucketDto::resourceId)
                        .thenComparing(LoadBucketDto::date)
                        .thenComparing(LoadBucketDto::shiftId))
                .toList();
        return new SrpLoadBucketResult(buckets, openings, horizonStart, horizonEnd);
    }

    private static String resolveResourceLabel(String resourceId) {
        return resourceId;
    }

    public record SrpLoadBucketResult(
            List<LoadBucketDto> buckets,
            List<LineOpeningSuggestionDto> openings,
            LocalDate horizonStart,
            LocalDate horizonEnd) {
    }
}
