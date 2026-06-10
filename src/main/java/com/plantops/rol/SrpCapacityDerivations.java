package com.plantops.rol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SrpCapacityDerivations {

    private SrpCapacityDerivations() {
    }

    public static DerivationRegistry register(com.plantops.ontology.OntologyGraph graph) {
        return new DerivationRegistry(derivations(graph));
    }

    public static List<Derivation> derivations(com.plantops.ontology.OntologyGraph graph) {
        List<Derivation> derivations = new ArrayList<>();
        for (com.plantops.ontology.period.StandardResourcePeriod period : graph.srpById().values()) {
            registerCapacityDerivations(derivations, period);
        }
        return derivations;
    }

    private static void registerCapacityDerivations(
            List<Derivation> derivations,
            com.plantops.ontology.period.StandardResourcePeriod period) {
        String id = period.getId();

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "availableCapacity"),
                Set.of(
                        Derivation.propertyKey(id, "totalCapacity"),
                        Derivation.propertyKey(id, "calendarDowntime"),
                        Derivation.propertyKey(id, "technicalDowntime")),
                (graph, targetKey) -> graph.srp(id).recalculateCapacityFields()));

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "freeCapacity"),
                Set.of(
                        Derivation.propertyKey(id, "availableCapacity"),
                        Derivation.propertyKey(id, "reservedCapacity")),
                (graph, targetKey) -> graph.srp(id).recalculateCapacityFields()));

        derivations.add(new Derivation(
                Derivation.propertyKey(id, "overloadCapacity"),
                Set.of(
                        Derivation.propertyKey(id, "availableCapacity"),
                        Derivation.propertyKey(id, "reservedCapacity")),
                (graph, targetKey) -> graph.srp(id).recalculateCapacityFields()));
    }
}
