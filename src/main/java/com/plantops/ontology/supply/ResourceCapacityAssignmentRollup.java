package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;

import java.util.LinkedHashMap;
import java.util.Map;

/** Σ ENT-RCA.assignedMinutes → ENT-SRP.reservedCapacity（ADR-15 · TODO-22 R2）。 */
public final class ResourceCapacityAssignmentRollup {

    private ResourceCapacityAssignmentRollup() {
    }

    public static Map<String, Double> reservedMinutesBySrpId(OntologyGraph graph) {
        Map<String, Double> reservedBySrpId = new LinkedHashMap<>();
        if (graph == null) {
            return reservedBySrpId;
        }
        for (ResourceCapacityAssignment rca : graph.resourceCapacityAssignmentsById().values()) {
            reservedBySrpId.merge(
                    rca.getStandardResourcePeriodId(),
                    (double) rca.getAssignedMinutes(),
                    Double::sum);
        }
        return reservedBySrpId;
    }
}
