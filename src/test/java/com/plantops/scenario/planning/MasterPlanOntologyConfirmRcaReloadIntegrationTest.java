package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.ontology.persistence.entity.OntResourceCapacityAssignmentEntity;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO-22 R5: confirm 后占用 SoT 为 committed ENT-RCA；reload Session ≡ optimize 图。
 */
@QuarkusTest
class MasterPlanOntologyConfirmRcaReloadIntegrationTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-RCA-R5";
    private static final String PRODUCT_CODE = "FG-OTD-RCA-R5";
    private static final String WORK_ORDER_NO = "WO-OTD-RCA-R5";
    private static final String RESOURCE_ID = "RES-OTD-RCA-R5";

    @Inject
    MasterPlanOntologySessionService sessionService;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Test
    @TestTransaction
    void confirmReloadMatchesSessionEntRca() throws Exception {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead();

        MasterPlanSessionDto created = sessionService.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        sessionService.optimize(created.sessionId());

        MasterPlanOntologySession beforeConfirm =
                sessionStore.require(created.sessionId(), WorkspaceResolver.currentWorkspaceId());
        Map<String, ResourceCapacityAssignment> rcaBefore =
                snapshotRca(beforeConfirm.graph());

        MasterPlanSessionConfirmResultDto confirmed = sessionService.confirm(created.sessionId());
        assertNotNull(confirmed.planVersionId());
        assertFalse(confirmed.planVersionId().isBlank());
        assertEquals(rcaBefore.size(), confirmed.allocationCount());
        assertTrue(confirmed.allocationCount() > 0);

        assertEquals(
                0,
                MasterPlanAllocationEntity.count("planVersionId = ?1", confirmed.planVersionId()));

        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        String revisionId = OntRevisionHeadEntity.findHead(
                        workspaceId, PlanVersionEntRcaOccupancy.planScope(confirmed.planVersionId()))
                .map(h -> h.revisionId)
                .orElseThrow();
        assertEquals(
                confirmed.allocationCount(),
                OntResourceCapacityAssignmentEntity.forRevision(workspaceId, revisionId).size());

        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
        MasterPlanSessionDto reloaded = sessionService.create(
                new CreateMasterPlanSessionRequest(confirmed.planVersionId(), null));
        MasterPlanOntologySession afterReload =
                sessionStore.require(reloaded.sessionId(), WorkspaceResolver.currentWorkspaceId());
        assertRcaParity(rcaBefore, snapshotRca(afterReload.graph()));
    }

    private static Map<String, ResourceCapacityAssignment> snapshotRca(OntologyGraph graph) {
        return graph.resourceCapacityAssignmentsById().values().stream()
                .collect(Collectors.toMap(ResourceCapacityAssignment::getId, rca -> rca));
    }

    private static void assertRcaParity(
            Map<String, ResourceCapacityAssignment> expected,
            Map<String, ResourceCapacityAssignment> actual) {
        assertEquals(expected.size(), actual.size());
        for (ResourceCapacityAssignment rca : expected.values()) {
            ResourceCapacityAssignment restored = actual.get(rca.getId());
            assertNotNull(restored, "missing RCA " + rca.getId());
            assertEquals(rca.getOperationId(), restored.getOperationId());
            assertEquals(rca.getStandardResourcePeriodId(), restored.getStandardResourcePeriodId());
            assertEquals(rca.getAssignedMinutes(), restored.getAssignedMinutes());
            assertEquals(rca.isLocked(), restored.isLocked());
        }
    }

    private void ensureFixture(LocalDate planningStart) {
        PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(PLAN_VERSION_ID);
        if (planVersion == null) {
            planVersion = new PlanVersionEntity();
            planVersion.planVersionId = PLAN_VERSION_ID;
            planVersion.planType = "MASTER_PLAN";
            planVersion.planGeneratedTs = LocalDateTime.now();
            planVersion.stampWorkspace();
            planVersion.persist();
        }

        if (SalesOrderLineEntity.findByKey("SO-OTD-RCA-R5", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-OTD-RCA-R5";
            line.salesOrderLineNo = 1;
            line.productCode = PRODUCT_CODE;
            line.orderQty = new BigDecimal("80");
            line.dueDate = planningStart.plusDays(10);
            line.priority = 5;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-RCA-R5";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("80");
            workOrder.needDate = planningStart.plusDays(10);
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, RESOURCE_ID) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = RESOURCE_ID;
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.setupTimeMinutes = 0;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }

        if (InventoryEntity.find(
                        "workspaceId = ?1 and productCode = ?2",
                        WorkspaceResolver.currentWorkspaceId(),
                        PRODUCT_CODE)
                .firstResult() == null) {
            InventoryEntity inventory = new InventoryEntity();
            inventory.stockingPointCode = OntologyIds.DEFAULT_FG;
            inventory.productCode = PRODUCT_CODE;
            inventory.onhandQty = new BigDecimal("10");
            inventory.reservedQty = BigDecimal.ZERO;
            inventory.qualityHoldQty = BigDecimal.ZERO;
            inventory.stampWorkspace();
            inventory.persist();
        }
    }

    private void refreshOntWorkspaceHead() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        ontologyPersistence.importCommittedP0(
                workspaceId, ontologyLoader.loadForPlanVersion(PLAN_VERSION_ID));
        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
    }
}
