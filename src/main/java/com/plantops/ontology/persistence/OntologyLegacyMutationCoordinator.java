package com.plantops.ontology.persistence;

import com.plantops.config.OntologyLegacyDualWriteFeature;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * P4 migration: after legacy supply-side mutations, refresh ont supply orders and drop authoritative graph cache.
 */
@ApplicationScoped
public class OntologyLegacyMutationCoordinator {

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyLegacyDualWriteFeature dualWriteFeature;

    @Inject
    OntologyLegacyDualWriteService dualWriteService;

    @Transactional
    public void afterWorkOrdersChanged(String workspaceId) {
        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
        if (!dualWriteFeature.enabled()) {
            return;
        }
        String revisionId = revisionService.resolveWorkspaceHeadRevisionId(workspaceId);
        if (revisionId != null) {
            dualWriteService.syncSupplyOrdersFromWorkOrders(workspaceId, revisionId);
        }
    }
}
