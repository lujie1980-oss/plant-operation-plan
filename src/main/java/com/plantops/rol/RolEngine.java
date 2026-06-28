package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.SupplyOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    public static RolEngine withSrpCapacityRules(OntologyGraph graph) {
        return new RolEngine(graph, SrpCapacityDerivations.register(graph));
    }

    public static RolEngine withMasterPlanRules(OntologyGraph graph) {
        List<Derivation> all = new ArrayList<>();
        all.addAll(PispPeriodDerivations.derivations(graph));
        all.addAll(PispMrpDerivations.derivations(graph));
        all.addAll(SrpCapacityDerivations.derivations(graph));
        all.addAll(OperationTimeWindowDerivations.derivations(graph));
        return new RolEngine(graph, new DerivationRegistry(all));
    }

    public void applyPropertyChange(ProductInStockingPointPeriod node, String property, double value) {
        setPisppProperty(node, property, value);
        propagateFrom(node.getId(), property);
        if ("plannedSupplyTotalOptimized".equals(property)) {
            propagateFrom(node.getId(), "plannedSupplyTotal");
        }
    }

    public void applyPropertyChange(StandardResourcePeriod node, String property, double value) {
        setSrpProperty(node, property, value);
        propagateFrom(node.getId(), property);
    }

    public void applySupplyOrderNeedDateChange(SupplyOrder node, LocalDate needDate) {
        node.setNeedDate(needDate);
        propagateFrom(node.getId(), "needDate");
    }

    private void propagateFrom(String nodeId, String property) {
        DirtySet dirty = new DirtySet();
        markDependents(Derivation.propertyKey(nodeId, property), dirty);
        propagator.propagate(dirty, registry, graph);
    }

    private void markDependents(String dependencyKey, DirtySet dirty) {
        for (Derivation dependent : registry.dependentsOf(dependencyKey)) {
            dirty.mark(dependent.targetKey());
        }
    }

    private static void setPisppProperty(ProductInStockingPointPeriod node, String property, double value) {
        switch (property) {
            case "onHand" -> node.setOnHand(value);
            case "plannedSupplyTotal" -> node.setPlannedSupplyTotal(value);
            case "plannedSupplyTotalMrp" -> node.setPlannedSupplyTotalMrp(value);
            case "plannedSupplyTotalOptimized" -> {
                node.setPlannedSupplyTotalOptimized(value);
                node.setPlannedSupplyTotal(value);
            }
            case "plannedDemandQuantityTotal" -> node.setPlannedDemandQuantityTotal(value);
            case "inventoryTargetQuantity" -> node.setInventoryTargetQuantity(value);
            default -> throw new IllegalArgumentException("Unsupported PISPP property: " + property);
        }
    }

    private static void setSrpProperty(StandardResourcePeriod node, String property, double value) {
        switch (property) {
            case "totalCapacity" -> node.setTotalCapacity(value);
            case "calendarDowntime" -> node.setCalendarDowntime(value);
            case "technicalDowntime" -> node.setTechnicalDowntime(value);
            case "reservedCapacity" -> node.setReservedCapacity(value);
            default -> throw new IllegalArgumentException("Unsupported SRP property: " + property);
        }
    }
}
