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
        validateLeafSrp(graph, rca, srp, errors);
    }

    private static void validateLeafSrp(
            OntologyGraph graph,
            ResourceCapacityAssignment rca,
            StandardResourcePeriod srp,
            List<String> errors) {
        for (com.plantops.ontology.period.Period period : graph.periodsOrdered()) {
            if (period.getId().equals(srp.getPeriodId()) && !period.isLeaf()) {
                errors.add("RCA " + rca.getId() + " must target leaf SRP, not parent period " + period.getId());
                return;
            }
        }
    }

    /** RULE-MP-08：并行工序组须落在同一 leaf SRP。 */
    public static List<String> validateParallelGroups(OntologyGraph graph) {
        if (graph == null) {
            return List.of("graph required");
        }
        Map<String, String> srpByGroup = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (ResourceCapacityAssignment rca : graph.resourceCapacityAssignmentsById().values()) {
            String groupId = rca.getParallelGroupId();
            if (groupId == null || groupId.isBlank()) {
                continue;
            }
            String existing = srpByGroup.putIfAbsent(groupId, rca.getStandardResourcePeriodId());
            if (existing != null && !existing.equals(rca.getStandardResourcePeriodId())) {
                errors.add("parallel group " + groupId
                        + " spans SRP " + existing + " and " + rca.getStandardResourcePeriodId());
            }
        }
        return List.copyOf(errors);
    }

    public static List<String> validateAll(OntologyGraph graph) {
        List<String> errors = new ArrayList<>(validate(graph));
        errors.addAll(validateParallelGroups(graph));
        return List.copyOf(errors);
    }
}
