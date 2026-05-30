package com.plantops.scenario.planning;

import com.plantops.persistence.entity.ProductionLineEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 细排工艺：资源 ↔ 产线解析。 */
final class DetailScheduleRoutingSupport {

    private DetailScheduleRoutingSupport() {
    }

    static List<String> lineIdsForResources(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> allowedResources = new LinkedHashSet<>(resourceIds);
        LinkedHashSet<String> lineIds = new LinkedHashSet<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.lineId != null && line.resourceId != null && allowedResources.contains(line.resourceId)) {
                lineIds.add(line.lineId);
            }
        }
        return List.copyOf(lineIds);
    }

    static List<String> resourceIdsForLines(List<String> lineIds) {
        if (lineIds == null || lineIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> allowedLines = new LinkedHashSet<>(lineIds);
        LinkedHashSet<String> resourceIds = new LinkedHashSet<>();
        for (ProductionLineEntity line : ProductionLineEntity.listInWorkspace()) {
            if (line.lineId != null && allowedLines.contains(line.lineId) && line.resourceId != null) {
                resourceIds.add(line.resourceId);
            }
        }
        return List.copyOf(new ArrayList<>(resourceIds));
    }
}
