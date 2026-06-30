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

/**
 * Assembles {@link OntologyGraph} from ont_* rows for a given revision (ADR-09 · RULE-PERS-01).
 * P1 restores V65 P0 entities; master / supply chain extensions follow in later Flyway versions.
 */
@ApplicationScoped
public class OntologyRestorer {

    @Inject
    OntologyRevisionService revisionService;

    public OntologyGraph loadRevision(String workspaceId, String revisionId) {
        revisionService.requireRevision(workspaceId, revisionId);

        OntologyGraph.Builder builder = OntologyGraph.builder();

        for (OntDemandEntity row : OntDemandEntity.forRevision(workspaceId, revisionId)) {
            builder.demand(OntologyEntityMapper.toDemand(row));
        }
        for (OntSupplyOrderEntity row : OntSupplyOrderEntity.forRevision(workspaceId, revisionId)) {
            builder.supplyOrder(OntologyEntityMapper.toSupplyOrder(row));
        }
        for (OntOperationEntity row : OntOperationEntity.forRevision(workspaceId, revisionId)) {
            builder.operation(OntologyEntityMapper.toOperation(row));
        }
        for (OntFulfillmentEntity row : OntFulfillmentEntity.forRevision(workspaceId, revisionId)) {
            builder.fulfillment(OntologyEntityMapper.toFulfillment(row));
        }
        for (OntPisppEntity row : OntPisppEntity.forRevision(workspaceId, revisionId)) {
            builder.pispPeriod(OntologyEntityMapper.toPispp(row));
        }
        for (OntSrpEntity row : OntSrpEntity.forRevision(workspaceId, revisionId)) {
            builder.standardResourcePeriod(OntologyEntityMapper.toSrp(row));
        }

        return builder.build();
    }
}
