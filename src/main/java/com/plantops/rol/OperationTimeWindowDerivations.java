package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class OperationTimeWindowDerivations {

    private OperationTimeWindowDerivations() {
    }

    /** 整链重算：latest 自尾 JIT 倒推（D14），earliest 自首正排；窗口空 → infeasible。 */
    public static void recalculate(OntologyGraph graph, String supplyOrderId, LocalDate planningStart) {
        SupplyOrder supplyOrder = graph.supplyOrder(supplyOrderId);
        List<Operation> operations = graph.operationsForSupplyOrder(supplyOrderId);
        if (supplyOrder == null || operations.isEmpty()) {
            return;
        }
        LocalDate latest = supplyOrder.getNeedDate() != null ? supplyOrder.getNeedDate() : planningStart;
        for (int i = operations.size() - 1; i >= 0; i--) {
            Operation operation = operations.get(i);
            operation.setLatestPossibleEnd(latest);
            latest = latest.minusDays(minutesToDays(operation.getProductionTimeMinutes()));
        }
        LocalDate earliest = planningStart;
        for (Operation operation : operations) {
            operation.setEarliestPossibleStart(earliest);
            operation.setInfeasible(earliest.isAfter(operation.getLatestPossibleEnd()));
            earliest = earliest.plusDays(minutesToDays(operation.getProductionTimeMinutes()));
        }
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
                    (g, targetKey) -> recalculate(g, supplyOrderId, planningStart)));
        }
        return derivations;
    }

    private static long minutesToDays(double minutes) {
        return Math.max(0, Math.round(Math.ceil(minutes / 1440.0)));
    }
}
