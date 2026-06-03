package com.plantops.scenario.batch;

import com.plantops.masterdata.FactoryCalendarService;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.scenario.ProductRoutingSteps;

import java.util.HashSet;
import java.util.Set;

/** 自动拆批启发式：从工艺涉及产线的班产能取值，不读计划参数。 */
public final class BatchSplitCapacityHelper {

    private BatchSplitCapacityHelper() {
    }

    public static int perShiftMinutesForProduct(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return FactoryCalendarService.DEFAULT_PER_SHIFT_MINUTES;
        }
        int max = 0;
        for (ProductRoutingSteps.Operation operation : ProductRoutingSteps.operationsForProduct(productCode)) {
            for (ProductionLineEntity line : linesForResources(operation.allowedResourceIds())) {
                if (line.lineCapacityPerShift > 0) {
                    max = Math.max(max, line.lineCapacityPerShift);
                }
            }
        }
        return max > 0 ? max : FactoryCalendarService.DEFAULT_PER_SHIFT_MINUTES;
    }

    private static Iterable<ProductionLineEntity> linesForResources(java.util.List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return java.util.List.of();
        }
        Set<String> allowed = new HashSet<>(resourceIds);
        java.util.List<ProductionLineEntity> lines = new java.util.ArrayList<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.resourceId != null && allowed.contains(line.resourceId)) {
                lines.add(line);
            }
        }
        return lines;
    }
}
