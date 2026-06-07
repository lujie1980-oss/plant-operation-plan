package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.ProductInStockingPointPeriod;

public final class RolEngine {

    private final OntologyGraph graph;
    private final DerivationRegistry registry;
    private final Propagator propagator = new Propagator();

    private RolEngine(OntologyGraph graph, DerivationRegistry registry) {
        this.graph = graph;
        this.registry = registry;
    }

    public static RolEngine withDefaultPispRules(OntologyGraph graph) {
        return new RolEngine(graph, PispPeriodDerivations.register(graph));
    }

    public void applyPropertyChange(ProductInStockingPointPeriod node, String property, double value) {
        setProperty(node, property, value);
        DirtySet dirty = new DirtySet();
        markDependents(Derivation.propertyKey(node.getId(), property), dirty);
        propagator.propagate(dirty, registry, graph);
    }

    private void markDependents(String dependencyKey, DirtySet dirty) {
        for (Derivation dependent : registry.dependentsOf(dependencyKey)) {
            dirty.mark(dependent.targetKey());
        }
    }

    private static void setProperty(ProductInStockingPointPeriod node, String property, double value) {
        switch (property) {
            case "onHand" -> node.setOnHand(value);
            case "plannedSupplyTotal" -> node.setPlannedSupplyTotal(value);
            case "plannedDemandQuantityTotal" -> node.setPlannedDemandQuantityTotal(value);
            case "inventoryTargetQuantity" -> node.setInventoryTargetQuantity(value);
            default -> throw new IllegalArgumentException("Unsupported PISPP property: " + property);
        }
    }
}
