package com.plantops.api.dto.masterplan;

import java.math.BigDecimal;
import java.util.List;

public final class MasterPlanDataModelDtos {

    private MasterPlanDataModelDtos() {
    }

    public record MasterPlanDataModelTreeDto(List<StockingPointNodeDto> stockingPoints) {
    }

    public record StockingPointNodeDto(
            String id,
            String stockingPointCode,
            String displayName,
            List<ProductInStockingPointNodeDto> pisps) {
    }

    public record ProductInStockingPointNodeDto(
            String pispId,
            String productCode,
            String productName,
            String stockingPointId,
            int bomTierFromTop,
            String bomTierLabel,
            boolean hasRouting) {
    }

    public record MasterPlanPispRoutingDetailDto(
            ProductInStockingPointNodeDto pisp,
            RoutingDto routing,
            List<RoutingStepDetailDto> steps) {
    }

    public record RoutingDto(
            String id,
            String pispId,
            String productCode,
            String routingName,
            int stepCount,
            int pathPriority) {
    }

    public record RoutingStepDetailDto(
            String id,
            String routingId,
            int sequenceNo,
            String operationName,
            List<RoutingStepOnStandardResourceDto> standardResources,
            List<RoutingStepInputMaterialDto> inputMaterials,
            List<RoutingStepOutputMaterialDto> outputMaterials) {
    }

    public record RoutingStepOnStandardResourceDto(
            String id,
            String routingStepId,
            String standardResourceId,
            Integer resourcePriority,
            int setupTimeMinutes,
            BigDecimal processTimeSeconds) {
    }

    public record RoutingStepInputMaterialDto(
            String id,
            String routingStepId,
            String componentProductCode,
            double componentQtyPer,
            boolean critical) {
    }

    public record RoutingStepOutputMaterialDto(
            String id,
            String routingStepId,
            String outputProductCode,
            double outputQtyPer) {
    }
}
