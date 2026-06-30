package com.plantops.ontology.persistence;

import com.plantops.ontology.persistence.entity.OntRevisionEntity;
import com.plantops.ontology.persistence.support.OntologyWorkOrderParity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.WorkOrderService;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AC-PERS-04: legacy work_order rows reconcile 1:1 to ont_supply_order on confirm promote.
 */
@QuarkusTest
class OntologyLegacyDualWriteIntegrationTest {

    @Inject
    OntologyPersistenceService persistence;

    @Inject
    OntologyLegacyDualWriteService dualWriteService;

    @Inject
    OntologyRevisionService revisionService;

    @Test
    @TestTransaction
    void syncSupplyOrdersFromWorkOrdersAlignsOntRows() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        String revisionId = revisionService.newRevisionId();
        revisionService.createRevision(
                workspaceId, revisionId, "COMMITTED", "FULL", null, null, null);

        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = "WO-PERS-04";
        wo.productCode = "FG-PERS-04";
        wo.quantity = new BigDecimal("120");
        wo.needDate = LocalDate.of(2026, 8, 1);
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.resourceId = "RES-PERS-04";
        wo.salesOrderNo = "SO-PERS-04";
        wo.salesOrderLineNo = 1;
        wo.sequenceNo = WorkOrderEntity.nextSequenceNo();
        wo.stampWorkspace();
        wo.persist();

        int synced = dualWriteService.syncSupplyOrdersFromWorkOrders(workspaceId, revisionId);
        assertEquals(WorkOrderEntity.list("workspaceId", workspaceId).size(), synced);

        OntologyWorkOrderParity.assertSupplyOrdersAlignWithWorkOrders(
                workspaceId, revisionId, WorkOrderEntity.list("workspaceId", workspaceId));
    }

    @Test
    @TestTransaction
    void promoteDraftReconcilesWorkOrdersWhenDualWriteEnabled() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        String sessionId = "SES-PERS-04";
        var graph = com.plantops.ontology.persistence.support.OntologyPersistenceTestFixtures.sampleP0Graph();
        String baseRevisionId = persistence.importCommittedP0(workspaceId, graph);

        persistence.createDraftSession(
                workspaceId,
                sessionId,
                baseRevisionId,
                graph,
                java.time.LocalDateTime.now().plusHours(8),
                null);

        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = "SO-PERS-01";
        wo.productCode = "FG-PERS-01";
        wo.quantity = new BigDecimal("100");
        wo.needDate = LocalDate.of(2026, 7, 5);
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;
        wo.sourceType = WorkOrderEntity.SOURCE_MRP;
        wo.resourceId = "RES-PERS-01";
        wo.salesOrderNo = "SO-PERS-01";
        wo.salesOrderLineNo = 1;
        wo.sequenceNo = WorkOrderEntity.nextSequenceNo();
        wo.stampWorkspace();
        wo.persist();

        var outcome = persistence.promoteDraftToCommitted(workspaceId, sessionId, "PV-PERS-04");
        OntRevisionEntity committed = revisionService.requireRevision(workspaceId, outcome.revisionId());
        assertEquals("COMMITTED", committed.status);

        OntologyWorkOrderParity.assertSupplyOrdersAlignWithWorkOrders(
                workspaceId,
                outcome.revisionId(),
                WorkOrderEntity.list("workspaceId", workspaceId));
    }
}
