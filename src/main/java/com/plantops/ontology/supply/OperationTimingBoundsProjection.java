package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从本体工序时间窗聚合工单级 {@link WorkOrderTimingBoundsContext}（直驱求解）。 */
public final class OperationTimingBoundsProjection {

    private OperationTimingBoundsProjection() {
    }

    public static WorkOrderTimingBoundsContext fromGraph(OntologyGraph graph) {
        if (graph == null) {
            return WorkOrderTimingBoundsContext.empty();
        }
        Map<String, LocalDateTime> earliestBySupplyOrder = new LinkedHashMap<>();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            List<Operation> operations = graph.operationsForSupplyOrder(supplyOrder.getId());
            if (operations.isEmpty()) {
                continue;
            }
            LocalDateTime earliest = operations.stream()
                    .map(Operation::getEarliestPossibleStartTotal)
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            if (earliest != null) {
                earliestBySupplyOrder.put(supplyOrder.getId(), earliest);
            }
        }
        return new WorkOrderTimingBoundsContext(earliestBySupplyOrder);
    }
}
