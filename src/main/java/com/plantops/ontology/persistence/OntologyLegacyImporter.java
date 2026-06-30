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

    @Transactional
    public void importP0(String workspaceId, String revisionId, OntologyGraph graph) {
        revisionService.requireRevision(workspaceId, revisionId);

        graph.demandsById().values().forEach(d ->
                OntologyEntityMapper.fromDemand(d, workspaceId, revisionId).persist());
        graph.supplyOrdersById().values().forEach(so ->
                OntologyEntityMapper.fromSupplyOrder(so, workspaceId, revisionId).persist());
        graph.operationsById().values().forEach(op ->
                OntologyEntityMapper.fromOperation(op, workspaceId, revisionId).persist());
        graph.fulfillments().forEach(ff ->
                OntologyEntityMapper.fromFulfillment(ff, workspaceId, revisionId).persist());
        graph.pispPeriodsById().values().forEach(p ->
                OntologyEntityMapper.fromPispp(p, workspaceId, revisionId).persist());
        graph.srpById().values().forEach(srp ->
                OntologyEntityMapper.fromSrp(srp, workspaceId, revisionId).persist());
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
