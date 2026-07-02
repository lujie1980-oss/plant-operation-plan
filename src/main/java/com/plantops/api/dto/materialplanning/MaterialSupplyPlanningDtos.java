package com.plantops.api.dto.materialplanning;

import com.plantops.api.dto.MaterialBalancePeriodDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MaterialSupplyPlanningDtos {

    private MaterialSupplyPlanningDtos() {
    }

    public record SupplyRoutingStepSummaryDto(
            int sequenceNo,
            String operationName,
            String primaryResourceId) {
    }

    public record SupplyRoutingCandidateDto(
            String routingId,
            int pathPriority,
            String routingName,
            int stepCount,
            List<SupplyRoutingStepSummaryDto> steps,
            LocalDateTime earliestAchievableTime) {
    }

    public record CreateSupplyPlanRequest(
            String mode,
            String periodFrom,
            String periodTo,
            Double quantity,
            String routingId,
            LocalDate needDate) {
    }

    public record SupplyPlanOrderSummaryDto(
            String supplyOrderId,
            String productCode,
            double quantity,
            LocalDate needDate) {
    }

    public record CreateSupplyPlanResultDto(
            List<SupplyPlanOrderSummaryDto> supplyOrderIds,
            String routingId,
            LocalDateTime earliestAchievableTime,
            MaterialBalancePeriodDto updatedPisppSummary) {
    }
}
