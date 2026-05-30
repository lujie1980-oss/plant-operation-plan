package com.plantops.api.dto;

import java.util.List;

public record DashboardSummaryDto(
        double demandFulfillmentRatePct,
        double capacityUtilizationPct,
        double materialShortageRatePct,
        int totalDemandLines,
        int fulfilledCount,
        int unfulfilledCount,
        int shortageCount,
        int overloadedBucketCount,
        String latestMasterPlanVersionId,
        String latestDetailPlanVersionId,
        List<DemandPoolEntryDto> unfulfilledDemands,
        List<DemandPoolEntryDto> shortageAffectedOrders,
        List<LoadBucketDto> highUtilizationBuckets
) {
}
