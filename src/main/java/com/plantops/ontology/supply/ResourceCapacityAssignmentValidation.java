package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.StandardResourcePeriod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ENT-RCA 键与分钟守恒校验（TODO-22 R1）。 */
public final class ResourceCapacityAssignmentValidation {

    private ResourceCapacityAssignmentValidation() {
    }

    public static List<String> validate(OntologyGraph graph) {
        if (graph == null) {
            return List.of("graph required");
        }
        List<String> errors = new ArrayList<>();
        Map<String, Integer> assignedByOperation = new LinkedHashMap<>();
        for (ResourceCapacityAssignment rca : graph.resourceCapacityAssignmentsById().values()) {
            validateBinding(graph, rca, errors);
            assignedByOperation.merge(rca.getOperationId(), rca.getAssignedMinutes(), Integer::sum);
        }
        for (ResourceCapacityAssignment rca : graph.resourceCapacityAssignmentsById().values()) {
            int expected = rca.getOperationTotalMinutes();
            int actual = assignedByOperation.getOrDefault(rca.getOperationId(), 0);
            if (expected != actual) {
                errors.add("operation " + rca.getOperationId()
                        + " assignedMinutes sum " + actual + " != operationTotalMinutes " + expected);
                break;
            }
        }
        return List.copyOf(errors);
    }

    private static void validateBinding(
            OntologyGraph graph, ResourceCapacityAssignment rca, List<String> errors) {
        Operation operation = graph.operation(rca.getOperationId());
        if (operation == null) {
            errors.add("RCA " + rca.getId() + " references missing operation " + rca.getOperationId());
            return;
        }
        OperationOnStandardResource oosr = graph.operationOnStandardResource(rca.getOperationOnStandardResourceId());
        if (oosr == null) {
            errors.add("RCA " + rca.getId() + " references missing OOSR " + rca.getOperationOnStandardResourceId());
            return;
        }
        if (!operation.getId().equals(oosr.getOperationId())) {
            errors.add("RCA " + rca.getId() + " OOSR " + oosr.getId()
                    + " does not belong to operation " + operation.getId());
        }
        StandardResourcePeriod srp = graph.srp(rca.getStandardResourcePeriodId());
        if (srp == null) {
            errors.add("RCA " + rca.getId() + " references missing SRP " + rca.getStandardResourcePeriodId());
            return;
        }
        if (!oosr.getStandardResourceId().equals(srp.getStandardResourceId())) {
            errors.add("RCA " + rca.getId() + " OOSR resource " + oosr.getStandardResourceId()
                    + " != SRP resource " + srp.getStandardResourceId());
        }
    }
}
