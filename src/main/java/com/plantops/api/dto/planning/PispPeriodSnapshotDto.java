package com.plantops.api.dto.planning;

public record PispPeriodSnapshotDto(
        String id,
        String pispId,
        String periodId,
        double onHand,
        double plannedSupplyTotal,
        double plannedSupplyTotalMrp,
        double plannedSupplyTotalOptimized,
        double plannedDemandQuantityTotal,
        double plannedInventoryLevel,
        double stockShortageQuantity) {
}
