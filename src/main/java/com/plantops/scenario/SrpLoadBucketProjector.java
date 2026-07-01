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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从本体 leaf {@link StandardResourcePeriod} 投影统一产能分析页的 {@link LoadBucketDto}（TODO-23 S3）。
 */
@ApplicationScoped
public class SrpLoadBucketProjector {

    @Inject
    CapacityBucketWorkOrderResolver workOrderResolver;

    @Inject
    ScheduleFeedbackService scheduleFeedbackService;

    public SrpLoadBucketResult project(OntologyGraph graph, String masterPlanVersionId, int overloadThresholdPct) {
        List<Period> periods = graph.periodsOrdered();
        if (periods.isEmpty()) {
            LocalDate today = LocalDate.now();
            return new SrpLoadBucketResult(List.of(), List.of(), today, today, 0, 0);
        }

        LocalDate horizonStart = periods.get(0).getStartDate();
        LocalDate horizonEnd = periods.get(periods.size() - 1).getEndDate();
        Map<String, LoadBucketDto> byBucketKey = new LinkedHashMap<>();
        List<LineOpeningSuggestionDto> openings = new ArrayList<>();
        int leafSrpCount = 0;
        int overloadedLeafSrpCount = 0;

        for (StandardResourcePeriod srp : SrpLeafCapacitySupport.leafSrps(graph)) {
            leafSrpCount++;
            if (SrpLeafCapacitySupport.overloadedByRule(srp)) {
                overloadedLeafSrpCount++;
            }

            Period period = SrpLeafCapacitySupport.periodFor(graph, srp.getPeriodId());
            if (period == null) {
                continue;
            }
            String resourceId = srp.getStandardResourceId();
            LocalDate date = period.getStartDate();
            String shiftId = SrpLeafCapacitySupport.shiftIdFor(period);

            TimeslotHorizonService.BucketKey key = new TimeslotHorizonService.BucketKey(
                    date, shiftId, date, period.getEndDate(), TimeslotGranularity.DAY);
            List<CapacityBucketWorkOrderDto> workOrders =
                    workOrderResolver.resolve(resourceId, date, shiftId, masterPlanVersionId);

            int demand = workOrders.stream().mapToInt(CapacityBucketWorkOrderDto::loadMinutes).sum();
            int available = (int) Math.round(Math.max(0, srp.getAvailableCapacity()));
            if (demand == 0 && srp.getReservedCapacity() > 0) {
                demand = (int) Math.round(srp.getReservedCapacity());
            }

            int feedbackLocked = scheduleFeedbackService.frozenMinutesForCapacityBucket(resourceId, key);
            int utilization = SrpLeafCapacitySupport.utilizationPct(srp);
            boolean overloaded = SrpLeafCapacitySupport.overloadedByThreshold(srp, overloadThresholdPct);
            String bucketId = CapacityService.bucketKey(resourceId, date, shiftId);

            byBucketKey.put(
                    bucketId,
                    new LoadBucketDto(
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

        List<LoadBucketDto> buckets = byBucketKey.values().stream()
                .sorted(Comparator
                        .comparing(LoadBucketDto::resourceId)
                        .thenComparing(LoadBucketDto::date)
                        .thenComparing(LoadBucketDto::shiftId))
                .toList();
        return new SrpLoadBucketResult(
                buckets, openings, horizonStart, horizonEnd, leafSrpCount, overloadedLeafSrpCount);
    }

    public record SrpLoadBucketResult(
            List<LoadBucketDto> buckets,
            List<LineOpeningSuggestionDto> openings,
            LocalDate horizonStart,
            LocalDate horizonEnd,
            int leafSrpCount,
            int overloadedLeafSrpCount) {
    }
}
