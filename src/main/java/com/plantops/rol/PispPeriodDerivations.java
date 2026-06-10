package com.plantops.rol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PispPeriodDerivations {

    private PispPeriodDerivations() {
    }

    public static DerivationRegistry register(com.plantops.ontology.OntologyGraph graph) {
        return new DerivationRegistry(derivations(graph));
    }

    public static List<Derivation> derivations(com.plantops.ontology.OntologyGraph graph) {
        List<Derivation> derivations = new ArrayList<>();
        Map<String, Map<String, com.plantops.ontology.period.ProductInStockingPointPeriod>> byPispAndPeriod =
                indexByPispAndPeriod(graph);
        List<String> periodIds = graph.periodsOrdered().stream()
                .map(com.plantops.ontology.period.Period::getId)
                .toList();

        for (Map<String, com.plantops.ontology.period.ProductInStockingPointPeriod> periodsById :
                byPispAndPeriod.values()) {
            for (int i = 0; i < periodIds.size(); i++) {
                String periodId = periodIds.get(i);
                com.plantops.ontology.period.ProductInStockingPointPeriod current = periodsById.get(periodId);
                if (current == null) {
                    continue;
                }
                registerSamePeriodDerivations(derivations, current);
                if (i + 1 < periodIds.size()) {
                    com.plantops.ontology.period.ProductInStockingPointPeriod next =
                            periodsById.get(periodIds.get(i + 1));
                    if (next != null) {
                        registerOnHandRollDerivation(derivations, current, next);
                    }
                }
            }
        }
        return derivations;
    }

    private static void registerSamePeriodDerivations(
            List<Derivation> derivations,
            com.plantops.ontology.period.ProductInStockingPointPeriod period) {
        String id = period.getId();

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "plannedInventoryLevel"),
                Set.of(
                        Derivation.propertyKey(id, "onHand"),
                        Derivation.propertyKey(id, "plannedSupplyTotal"),
                        Derivation.propertyKey(id, "plannedDemandQuantityTotal")),
                (graph, targetKey) -> graph.pispPeriod(id).recalculatePlanningFields()));

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "replenishedInventoryLevel"),
                Set.of(
                        Derivation.propertyKey(id, "onHand"),
                        Derivation.propertyKey(id, "plannedSupplyTotal")),
                (graph, targetKey) -> graph.pispPeriod(id).recalculatePlanningFields()));

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "stockShortageQuantity"),
                Set.of(
                        Derivation.propertyKey(id, "plannedDemandQuantityTotal"),
                        Derivation.propertyKey(id, "inventoryTargetQuantity"),
                        Derivation.propertyKey(id, "replenishedInventoryLevel")),
                (graph, targetKey) -> graph.pispPeriod(id).recalculatePlanningFields()));
    }

    private static void registerOnHandRollDerivation(
            List<Derivation> derivations,
            com.plantops.ontology.period.ProductInStockingPointPeriod current,
            com.plantops.ontology.period.ProductInStockingPointPeriod next) {
        derivations.add(new Derivation(
                Derivation.propertyKey(next.getId(), "onHand"),
                Set.of(Derivation.propertyKey(current.getId(), "plannedInventoryLevel")),
                (graph, targetKey) -> {
                    com.plantops.ontology.period.ProductInStockingPointPeriod previous =
                            graph.pispPeriod(current.getId());
                    com.plantops.ontology.period.ProductInStockingPointPeriod target =
                            graph.pispPeriod(next.getId());
                    target.setOnHand(previous.getPlannedInventoryLevel());
                }));
    }

    private static Map<String, Map<String, com.plantops.ontology.period.ProductInStockingPointPeriod>>
            indexByPispAndPeriod(com.plantops.ontology.OntologyGraph graph) {
        Map<String, Map<String, com.plantops.ontology.period.ProductInStockingPointPeriod>> index =
                new HashMap<>();
        for (com.plantops.ontology.period.ProductInStockingPointPeriod period :
                graph.pispPeriodsById().values()) {
            index.computeIfAbsent(period.getPispId(), ignored -> new HashMap<>())
                    .put(period.getPeriodId(), period);
        }
        return index;
    }
}
