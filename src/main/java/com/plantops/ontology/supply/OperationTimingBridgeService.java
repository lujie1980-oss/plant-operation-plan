package com.plantops.ontology.supply;

import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.WorkOrderTimingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;

/** B.4：装载时桥接 {@link WorkOrderTimingService} → 工序级时间窗。 */
@ApplicationScoped
public class OperationTimingBridgeService {

    @Inject
    WorkOrderTimingService workOrderTimingService;

    public void applyToGraph(OntologyGraph graph, LocalDate planningStart) {
        if (graph == null) {
            return;
        }
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            applyForSupplyOrder(graph, supplyOrder.getId(), planningStart);
        }
    }

    public void applyForSupplyOrder(OntologyGraph graph, String supplyOrderId, LocalDate planningStart) {
        WorkOrderTimingWindowDto window = resolveWindow(supplyOrderId);
        OperationTimeWindowDerivations.recalculate(graph, supplyOrderId, planningStart, window);
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        if (supplyOrder != null && supplyOrder.getNeedDate() != null) {
            OperationTimeWindowDerivations.recalculateLatestDesired(
                    graph, supplyOrderId, planningStart, null);
        }
    }

    public WorkOrderTimingWindowDto resolveWindow(String supplyOrderId) {
        if (supplyOrderId == null || WorkOrderEntity.findByNo(supplyOrderId) == null) {
            return null;
        }
        return workOrderTimingService.compute(supplyOrderId, null);
    }
}
