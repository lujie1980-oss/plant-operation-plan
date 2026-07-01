package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.solver.masterplan.TimeSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ENT-RCA ↔ solver {@link com.plantops.solver.masterplan.ResourceCapacityAssignment} /
 * {@link TimeSlot} 投影（TODO-22 R3 · ADR-15）。
 */
public final class OntologyRcaProjector {

    private OntologyRcaProjector() {
    }

    public static String matchKey(String operationId, String standardResourcePeriodId) {
        return operationId + "@" + standardResourcePeriodId;
    }

    public static List<TimeSlot> eligibleTimeSlotsForSrp(
            OntologyGraph graph, String standardResourcePeriodId, List<TimeSlot> slots) {
        if (graph == null || standardResourcePeriodId == null || slots == null) {
            return List.of();
        }
        StandardResourcePeriod srp = graph.srp(standardResourcePeriodId);
        if (srp == null) {
            return List.of();
        }
        Period period = periodFor(graph, srp.getPeriodId());
        if (period == null) {
            return List.of();
        }
        List<TimeSlot> eligible = new ArrayList<>();
        for (TimeSlot slot : slots) {
            if (!srp.getStandardResourceId().equals(slot.getResourceId())) {
                continue;
            }
            if (!slot.getDate().isBefore(period.getStartDate()) && !slot.getDate().isAfter(period.getEndDate())) {
                eligible.add(slot);
            }
        }
        return List.copyOf(eligible);
    }

    public static String resolveSrpIdForSlot(OntologyGraph graph, String resourceId, TimeSlot slot) {
        if (graph == null || resourceId == null || slot == null) {
            return null;
        }
        int seq = PeriodIndex.of(graph.periodsOrdered()).sequenceFor(slot.getDate());
        String srpId = OntologyIds.srpId(resourceId, seq);
        return graph.srp(srpId) != null ? srpId : null;
    }

    public static TimeSlot primaryTimeSlotForSrp(
            OntologyGraph graph, String standardResourcePeriodId, List<TimeSlot> slots) {
        List<TimeSlot> eligible = eligibleTimeSlotsForSrp(graph, standardResourcePeriodId, slots);
        return eligible.isEmpty() ? null : eligible.get(0);
    }

    /**
     * 将图中已存在的 ENT-RCA 占用写回 solver 候选（restore / explain 路径）。
     */
    public static void overlayOntologyOntoSolverCandidates(
            OntologyGraph graph,
            List<com.plantops.solver.masterplan.ResourceCapacityAssignment> solverAssignments,
            List<TimeSlot> slots) {
        if (graph == null
                || solverAssignments == null
                || graph.resourceCapacityAssignmentsById().isEmpty()) {
            return;
        }
        Map<String, ResourceCapacityAssignment> ontologyByKey = new LinkedHashMap<>();
        for (ResourceCapacityAssignment ontologyRca : graph.resourceCapacityAssignmentsById().values()) {
            ontologyByKey.put(
                    matchKey(ontologyRca.getOperationId(), ontologyRca.getStandardResourcePeriodId()),
                    ontologyRca);
        }
        for (com.plantops.solver.masterplan.ResourceCapacityAssignment solverRca : solverAssignments) {
            String srpId = resolveSolverSrpId(graph, solverRca, slots);
            if (srpId == null) {
                continue;
            }
            ResourceCapacityAssignment ontologyRca = ontologyByKey.get(matchKey(solverRca.getOperationId(), srpId));
            if (ontologyRca == null) {
                continue;
            }
            solverRca.setAssignedMinutes(ontologyRca.getAssignedMinutes());
            if (ontologyRca.getAssignedMinutes() > 0) {
                TimeSlot slot = primaryTimeSlotForSrp(graph, srpId, slots);
                if (slot != null) {
                    solverRca.setTimeSlot(slot);
                }
            }
        }
    }

    /**
     * CP-SAT 结果写回 ENT-RCA（assignedMinutes 与守恒字段）。
     */
    public static void syncOntologyFromSolverAssignments(
            OntologyGraph graph,
            List<com.plantops.solver.masterplan.ResourceCapacityAssignment> solverAssignments,
            List<TimeSlot> slots) {
        if (graph == null || solverAssignments == null || graph.resourceCapacityAssignmentsById().isEmpty()) {
            return;
        }
        Map<String, ResourceCapacityAssignment> ontologyByKey = new LinkedHashMap<>();
        for (ResourceCapacityAssignment ontologyRca : graph.resourceCapacityAssignmentsById().values()) {
            ontologyByKey.put(
                    matchKey(ontologyRca.getOperationId(), ontologyRca.getStandardResourcePeriodId()),
                    ontologyRca);
        }
        for (com.plantops.solver.masterplan.ResourceCapacityAssignment solverRca : solverAssignments) {
            if (solverRca.getAssignedMinutes() <= 0) {
                continue;
            }
            String srpId = resolveSolverSrpId(graph, solverRca, slots);
            if (srpId == null) {
                continue;
            }
            ResourceCapacityAssignment ontologyRca = ontologyByKey.get(matchKey(solverRca.getOperationId(), srpId));
            if (ontologyRca != null) {
                ontologyRca.setAssignedMinutes(solverRca.getAssignedMinutes());
            }
        }
    }

    private static String resolveSolverSrpId(
            OntologyGraph graph,
            com.plantops.solver.masterplan.ResourceCapacityAssignment solverRca,
            List<TimeSlot> slots) {
        if (solverRca.getTimeSlot() != null) {
            return resolveSrpIdForSlot(graph, solverRca.getResourceId(), solverRca.getTimeSlot());
        }
        if (solverRca.getOperationId() == null || solverRca.getResourceId() == null) {
            return null;
        }
        for (ResourceCapacityAssignment ontologyRca : graph.resourceCapacityAssignmentsById().values()) {
            if (!solverRca.getOperationId().equals(ontologyRca.getOperationId())) {
                continue;
            }
            OperationOnStandardResource oosr =
                    graph.operationOnStandardResource(ontologyRca.getOperationOnStandardResourceId());
            if (oosr != null && solverRca.getResourceId().equals(oosr.getStandardResourceId())) {
                return ontologyRca.getStandardResourcePeriodId();
            }
        }
        if (slots == null) {
            return null;
        }
        for (TimeSlot slot : slots) {
            if (!solverRca.getResourceId().equals(slot.getResourceId())) {
                continue;
            }
            String srpId = resolveSrpIdForSlot(graph, solverRca.getResourceId(), slot);
            if (srpId != null) {
                return srpId;
            }
        }
        return null;
    }

    private static Period periodFor(OntologyGraph graph, String periodId) {
        return graph.periodsOrdered().stream()
                .filter(period -> periodId.equals(period.getId()))
                .findFirst()
                .orElse(null);
    }
}
