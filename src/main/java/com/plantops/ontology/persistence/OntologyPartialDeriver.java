package com.plantops.ontology.persistence;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.persistence.entity.OntPisppEntity;
import com.plantops.ontology.persistence.entity.OntRevisionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Recomputes DERIVE entity kinds after PARTIAL restore (§5.16 · TODO-12 P5).
 * P5 skeleton: PISPP derived from parent FULL revision rows when forked.
 */
@ApplicationScoped
public class OntologyPartialDeriver {

    @Inject
    OntologyRevisionService revisionService;

    @Inject
    OntologyEntityPolicyService policyService;

    public OntologyGraph derive(String workspaceId, String revisionId, OntologyGraph storeOnly) {
        OntRevisionEntity rev = revisionService.requireRevision(workspaceId, revisionId);
        if (!OntologyEntityPolicyService.PERSISTENCE_PARTIAL.equals(rev.persistenceMode)) {
            return storeOnly;
        }

        OntologyGraph.Builder builder = OntologyGraph.builder();
        storeOnly.demandsById().values().forEach(builder::demand);
        storeOnly.supplyOrdersById().values().forEach(builder::supplyOrder);
        storeOnly.operationsById().values().forEach(builder::operation);
        storeOnly.fulfillments().forEach(builder::fulfillment);
        storeOnly.pispPeriodsById().values().forEach(builder::pispPeriod);
        storeOnly.srpById().values().forEach(builder::standardResourcePeriod);
        builder.periodsOrdered(storeOnly.periodsOrdered());

        if (policyService.shouldDerive(workspaceId, rev.persistenceMode, OntologyEntityKind.PISPP)) {
            derivePispp(workspaceId, rev, builder);
        }

        return builder.build();
    }

    private void derivePispp(
            String workspaceId, OntRevisionEntity revision, OntologyGraph.Builder builder) {
        String referenceRevisionId = revision.parentRevisionId;
        if (referenceRevisionId == null || referenceRevisionId.isBlank()) {
            return;
        }
        for (OntPisppEntity row : OntPisppEntity.forRevision(workspaceId, referenceRevisionId)) {
            ProductInStockingPointPeriod pispp = OntologyEntityMapper.toPispp(row);
            pispp.recalculatePlanningFields();
            builder.pispPeriod(pispp);
        }
    }
}
