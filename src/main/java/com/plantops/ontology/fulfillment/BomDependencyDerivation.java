package com.plantops.ontology.fulfillment;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.supply.BomDependency;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationInputMaterial;
import com.plantops.ontology.supply.Supply;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.solver.masterplan.BomDependencyEdge;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从 Demand → Fulfillment → Supply（工单产出）派生 {@link BomDependency}（D29）。
 */
@ApplicationScoped
public class BomDependencyDerivation {

    public void derive(OntologyGraph.Builder builder) {
        Map<String, List<Fulfillment>> fulfillmentsByDemand = indexWorkOrderFulfillmentsByDemand(builder);
        Map<String, List<Operation>> operationsBySupplyOrder = indexOperationsBySupplyOrder(builder);
        Map<String, List<OperationInputMaterial>> inputsByOperation = indexInputsByOperation(builder);

        Set<String> seen = new LinkedHashSet<>();
        for (SupplyOrder parent : builder.supplyOrdersById().values()) {
            for (Operation operation : operationsBySupplyOrder.getOrDefault(parent.getId(), List.of())) {
                for (OperationInputMaterial oim : inputsByOperation.getOrDefault(operation.getId(), List.of())) {
                    addEdgesForDemand(
                            builder,
                            parent.getId(),
                            oim.getDemandId(),
                            fulfillmentsByDemand,
                            seen);
                }
            }
        }
    }

    private static Map<String, List<Fulfillment>> indexWorkOrderFulfillmentsByDemand(
            OntologyGraph.Builder builder) {
        Map<String, List<Fulfillment>> byDemand = new HashMap<>();
        for (Fulfillment fulfillment : builder.fulfillments()) {
            if (fulfillment.getType() != FulfillmentType.WORK_ORDER_PEG) {
                continue;
            }
            byDemand.computeIfAbsent(fulfillment.getDemandId(), k -> new ArrayList<>()).add(fulfillment);
        }
        return byDemand;
    }

    private static Map<String, List<Operation>> indexOperationsBySupplyOrder(OntologyGraph.Builder builder) {
        Map<String, List<Operation>> bySupplyOrder = new HashMap<>();
        for (Operation operation : builder.operationsById().values()) {
            if (operation.getSupplyOrderId() == null) {
                continue;
            }
            bySupplyOrder
                    .computeIfAbsent(operation.getSupplyOrderId(), k -> new ArrayList<>())
                    .add(operation);
        }
        return bySupplyOrder;
    }

    private static Map<String, List<OperationInputMaterial>> indexInputsByOperation(
            OntologyGraph.Builder builder) {
        Map<String, List<OperationInputMaterial>> byOperation = new HashMap<>();
        for (OperationInputMaterial oim : builder.operationInputMaterialsById().values()) {
            byOperation.computeIfAbsent(oim.getOperationId(), k -> new ArrayList<>()).add(oim);
        }
        return byOperation;
    }

    private static void addEdgesForDemand(
            OntologyGraph.Builder builder,
            String parentSupplyOrderId,
            String demandId,
            Map<String, List<Fulfillment>> fulfillmentsByDemand,
            Set<String> seen) {
        for (Fulfillment fulfillment : fulfillmentsByDemand.getOrDefault(demandId, List.of())) {
            Supply supply = builder.suppliesById().get(fulfillment.getSupplyId());
            if (supply == null || supply.getSupplyOrderId() == null) {
                continue;
            }
            String childSupplyOrderId = supply.getSupplyOrderId();
            if (childSupplyOrderId.equals(parentSupplyOrderId)) {
                continue;
            }
            String edgeId = OntologyIds.bomDependencyId(parentSupplyOrderId, childSupplyOrderId);
            if (seen.add(edgeId)) {
                builder.bomDependency(new BomDependency(edgeId, parentSupplyOrderId, childSupplyOrderId));
            }
        }
    }

    /** 投影至 Timefold {@link BomDependencyEdge}（SO.id = workOrderNo）。 */
    public static List<BomDependencyEdge> toSolverEdges(OntologyGraph graph) {
        List<BomDependencyEdge> edges = new ArrayList<>(graph.bomDependencies().size());
        Set<String> seen = new HashSet<>();
        for (BomDependency dep : graph.bomDependencies()) {
            String key = dep.getParentSupplyOrderId() + "->" + dep.getChildSupplyOrderId();
            if (seen.add(key)) {
                edges.add(new BomDependencyEdge(dep.getParentSupplyOrderId(), dep.getChildSupplyOrderId()));
            }
        }
        return edges;
    }
}
