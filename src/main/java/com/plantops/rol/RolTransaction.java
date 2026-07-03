package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RolTransaction {

    public void apply(ChangeSet changeSet, OntologyGraph graph, RolEngine rolEngine) {
        for (ChangeOperation operation : changeSet.operations()) {
            applyOperation(operation, graph, rolEngine);
        }
    }

    private static void applyOperation(ChangeOperation operation, OntologyGraph graph, RolEngine rolEngine) {
        if (ChangeOperation.TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD.equals(operation.targetType())) {
            ProductInStockingPointPeriod target = graph.pispPeriod(operation.targetId());
            if (target == null) {
                throw new IllegalArgumentException("PISPP not found: " + operation.targetId());
            }
            rolEngine.applyPropertyChange(target, operation.property(), toDouble(operation.value()));
            return;
        }
        if (ChangeOperation.TARGET_STANDARD_RESOURCE_PERIOD.equals(operation.targetType())) {
            StandardResourcePeriod target = graph.srp(operation.targetId());
            if (target == null) {
                throw new IllegalArgumentException("SRP not found: " + operation.targetId());
            }
            rolEngine.applyPropertyChange(target, operation.property(), toDouble(operation.value()));
            return;
        }
        throw new IllegalArgumentException("Unsupported target type: " + operation.targetType());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Numeric value required: " + value);
    }
}
