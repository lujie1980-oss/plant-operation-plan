package com.plantops.scenario.planning;

import com.plantops.ontology.persistence.OntologyRevisionService;
import com.plantops.ontology.persistence.entity.OntResourceCapacityAssignmentEntity;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;

/**
 * Resolves whether a plan version's occupancy SoT is committed ENT-RCA (ADR-15 · TODO-22 R5).
 */
public final class PlanVersionEntRcaOccupancy {

    private PlanVersionEntRcaOccupancy() {}

    public static boolean hasCommittedEntRca(String workspaceId, String planVersionId) {
        if (workspaceId == null
                || workspaceId.isBlank()
                || planVersionId == null
                || planVersionId.isBlank()) {
            return false;
        }
        return OntRevisionHeadEntity.findHead(workspaceId, planScope(planVersionId))
                .map(head -> !OntResourceCapacityAssignmentEntity.forRevision(
                                workspaceId, head.revisionId)
                        .isEmpty())
                .orElse(false);
    }

    public static String planScope(String planVersionId) {
        return "PLAN:" + planVersionId.trim();
    }

    public static String workspaceScope() {
        return OntologyRevisionService.WORKSPACE_SCOPE;
    }
}
