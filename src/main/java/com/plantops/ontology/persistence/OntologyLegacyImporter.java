package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.entity.OntDemandEntity;
import com.plantops.ontology.persistence.entity.OntFulfillmentEntity;
import com.plantops.ontology.persistence.entity.OntOperationEntity;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.entity.OntSrpEntity;
import com.plantops.ontology.persistence.entity.OntSupplyOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Transitional exporter: in-memory ENT-OG P0 subset → ont_* rows (TODO-12 P4 migration aid).
 */
@ApplicationScoped
public class OntologyLegacyImporter {

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyP0UpsertService upsertService;

    @Inject
    OntologyEntityPolicyService policyService;

    @Transactional
    public void importP0(String workspaceId, String revisionId, OntologyGraph graph) {
        var rev = revisionService.requireRevision(workspaceId, revisionId);
        upsertService.replaceP0Graph(workspaceId, revisionId, graph, rev.persistenceMode);
    }

    @Transactional
    public String importCommittedP0(String workspaceId, OntologyGraph graph) {
        String revisionId = revisionService.newRevisionId();
        revisionService.createRevision(
                workspaceId, revisionId, "COMMITTED", "FULL", null, null, null);
        importP0(workspaceId, revisionId, graph);
        revisionService.setHead(workspaceId, OntologyRevisionService.WORKSPACE_SCOPE, revisionId);
        return revisionId;
    }

    /**
     * PARTIAL fork: STORE entities only; DERIVE kinds (e.g. PISPP) loaded via parent revision on restore.
     */
    @Transactional
    public String importPartialP0Fork(String workspaceId, OntologyGraph graph, String parentRevisionId) {
        policyService.seedDefaultPartialPolicies(workspaceId);
        String revisionId = revisionService.newRevisionId();
        revisionService.createRevision(
                workspaceId,
                revisionId,
                "COMMITTED",
                OntologyEntityPolicyService.PERSISTENCE_PARTIAL,
                parentRevisionId,
                null,
                null);
        upsertService.replaceP0Graph(
                workspaceId,
                revisionId,
                graph,
                OntologyEntityPolicyService.PERSISTENCE_PARTIAL);
        return revisionId;
    }

    @Transactional
    public void deleteP0Revision(String workspaceId, String revisionId) {
        OntDemandEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        OntSupplyOrderEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        OntOperationEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        OntFulfillmentEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        OntPisppEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        OntSrpEntity.delete("workspaceId = ?1 and revisionId = ?2", workspaceId, revisionId);
        revisionService.requireRevision(workspaceId, revisionId).delete();
    }
}
