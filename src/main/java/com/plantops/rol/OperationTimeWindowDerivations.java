package com.plantops.rol;

import com.plantops.api.dto.WorkOrderTimingWindowDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.supply.SupplyOrder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OperationTimeWindowDerivations {

    private OperationTimeWindowDerivations() {
    }

    public static void recalculate(OntologyGraph graph, String supplyOrderId, LocalDate planningStart) {
        recalculate(graph, supplyOrderId, planningStart, null);
    }

    public static void recalculate(
            OntologyGraph graph,
            String supplyOrderId,
            LocalDate planningStart,
            WorkOrderTimingWindowDto window) {
        com.plantops.ontology.supply.OperationTimeWindowDerivations.recalculate(
                graph, supplyOrderId, planningStart, window);
    }

    public static List<Derivation> derivations(OntologyGraph graph) {
        List<Derivation> derivations = new ArrayList<>();
        LocalDate planningStart = graph.periodsOrdered().isEmpty()
                ? LocalDate.now()
                : graph.periodsOrdered().get(0).getStartDate();
        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            String supplyOrderId = supplyOrder.getId();
            derivations.add(new Derivation(
                    Derivation.propertyKey(supplyOrderId, "operationTimeWindows"),
                    Set.of(Derivation.propertyKey(supplyOrderId, "needDate")),
                    (g, targetKey) -> com.plantops.ontology.supply.OperationTimeWindowDerivations
                            .recalculateLatestDesired(g, supplyOrderId, planningStart, null)));
        }
        return derivations;
    }
}
