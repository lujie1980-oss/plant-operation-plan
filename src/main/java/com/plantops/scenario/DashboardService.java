package com.plantops.scenario;

import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.DashboardSummaryDto;
import com.plantops.api.dto.DemandPoolEntryDto;
import com.plantops.api.dto.LoadBucketDto;
import com.plantops.persistence.entity.PlanVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class DashboardService {

    private static final Set<String> FULFILLED_STATUSES = Set.of("ON_TRACK", "PLANNED");

    @Inject
    DemandService demandService;

    @Inject
    CapacityService capacityService;

    public DashboardSummaryDto getSummary() {
        List<DemandPoolEntryDto> pool = demandService.getDemandPool();
        int total = pool.size();
        List<DemandPoolEntryDto> unfulfilled = pool.stream()
                .filter(e -> !FULFILLED_STATUSES.contains(e.fulfillmentStatus()))
                .toList();
        List<DemandPoolEntryDto> shortageAffected = pool.stream()
                .filter(e -> "SHORTAGE".equals(e.kittingStatus()))
                .toList();

        int fulfilled = total - unfulfilled.size();
        double fulfillmentPct = total > 0 ? round2(100.0 * fulfilled / total) : 100.0;
        double shortagePct = total > 0 ? round2(100.0 * shortageAffected.size() / total) : 0.0;

        CapacityAnalysisDto capacity = capacityService.analyze();
        List<LoadBucketDto> buckets = capacity.loadBuckets();
        double capacityPct = 0;
        int overloaded = 0;
        if (!buckets.isEmpty()) {
            double sum = 0;
            for (LoadBucketDto b : buckets) {
                sum += b.utilizationPct();
                if (b.overloaded()) {
                    overloaded++;
                }
            }
            capacityPct = round2(sum / buckets.size());
        }

        List<LoadBucketDto> hotBuckets = buckets.stream()
                .filter(b -> b.utilizationPct() >= 80 || b.overloaded())
                .sorted(Comparator.comparingDouble(LoadBucketDto::utilizationPct).reversed())
                .limit(20)
                .toList();

        return new DashboardSummaryDto(
                fulfillmentPct,
                capacityPct,
                shortagePct,
                total,
                fulfilled,
                unfulfilled.size(),
                shortageAffected.size(),
                overloaded,
                findLatestPlanVersionId("MASTER_PLAN"),
                findLatestPlanVersionId("DETAIL_SCHEDULE"),
                unfulfilled,
                shortageAffected,
                hotBuckets);
    }

    private static String findLatestPlanVersionId(String planType) {
        return PlanVersionEntity.listInWorkspace().stream()
                .filter(v -> planType.equals(v.planType))
                .max(Comparator.comparing(v -> v.planGeneratedTs))
                .map(v -> v.planVersionId)
                .orElse(null);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
