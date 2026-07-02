package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.persistence.entity.OntDemandEntity;
import com.plantops.ontology.persistence.entity.OntFulfillmentEntity;
import com.plantops.ontology.persistence.entity.OntOperationEntity;
import com.plantops.ontology.persistence.entity.OntPeriodEntity;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.entity.OntResourceCapacityAssignmentEntity;
import com.plantops.ontology.persistence.entity.OntRevisionEntity;
import com.plantops.ontology.persistence.entity.OntSrpEntity;
import com.plantops.ontology.persistence.entity.OntSupplyOrderEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Assembles {@link OntologyGraph} from ont_* rows for a given revision (ADR-09 · RULE-PERS-01).
 * P1 restores V65 P0 entities; PARTIAL revisions apply {@link OntologyPartialDeriver} after STORE load.
 */
@ApplicationScoped
public class OntologyRestorer {

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyEntityPolicyService policyService;

    @Inject
    OntologyPartialDeriver partialDeriver;

    public OntologyGraph loadRevision(String workspaceId, String revisionId) {
        OntRevisionEntity revision = revisionService.requireRevision(workspaceId, revisionId);
        String persistenceMode = revision.persistenceMode;

        OntologyGraph.Builder builder = OntologyGraph.builder();

        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.DEMAND)) {
            for (OntDemandEntity row : OntDemandEntity.forRevision(workspaceId, revisionId)) {
                builder.demand(OntologyEntityMapper.toDemand(row));
            }
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SUPPLY_ORDER)) {
            for (OntSupplyOrderEntity row : OntSupplyOrderEntity.forRevision(workspaceId, revisionId)) {
                builder.supplyOrder(OntologyEntityMapper.toSupplyOrder(row));
            }
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.OPERATION)) {
            for (OntOperationEntity row : OntOperationEntity.forRevision(workspaceId, revisionId)) {
                builder.operation(OntologyEntityMapper.toOperation(row));
            }
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.FULFILLMENT)) {
            for (OntFulfillmentEntity row : OntFulfillmentEntity.forRevision(workspaceId, revisionId)) {
                builder.fulfillment(OntologyEntityMapper.toFulfillment(row));
            }
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.PISPP)) {
            for (OntPisppEntity row : OntPisppEntity.forRevision(workspaceId, revisionId)) {
                builder.pispPeriod(OntologyEntityMapper.toPispp(row));
            }
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.PERIOD)) {
            builder.periodsOrdered(
                    OntPeriodEntity.forRevision(workspaceId, revisionId).stream()
                            .map(OntologyEntityMapper::toPeriod)
                            .toList());
        }
        if (policyService.shouldStore(workspaceId, persistenceMode, OntologyEntityKind.SRP)) {
            for (OntSrpEntity row : OntSrpEntity.forRevision(workspaceId, revisionId)) {
                builder.standardResourcePeriod(OntologyEntityMapper.toSrp(row));
            }
        }
        if (policyService.shouldStore(
                workspaceId, persistenceMode, OntologyEntityKind.RESOURCE_CAPACITY_ASSIGNMENT)) {
            for (OntResourceCapacityAssignmentEntity row :
                    OntResourceCapacityAssignmentEntity.forRevision(workspaceId, revisionId)) {
                builder.resourceCapacityAssignment(OntologyEntityMapper.toResourceCapacityAssignment(row));
            }
        }

        return partialDeriver.derive(workspaceId, revisionId, builder.build());
    }
}
