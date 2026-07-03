package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.ontology.persistence.entity.OntResourceCapacityAssignmentEntity;
import com.plantops.ontology.persistence.entity.OntRevisionHeadEntity;
import com.plantops.persistence.entity.InventoryEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanOntologyConfirmServiceTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-CONFIRM-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-CONFIRM-100";
    private static final String WORK_ORDER_NO = "WO-OTD-CONFIRM-001";
    private static final String RESOURCE_ID = "RES-OTD-CONFIRM-01";

    @Inject
    MasterPlanOntologySessionService service;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    @Test
    @TestTransaction
    void confirmPersistsEntRcaAndReturnsPlanVersionId() throws Exception {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead();

        MasterPlanSessionDto created = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        MasterPlanSessionOptimizeResultDto optimized = service.optimize(created.sessionId());
        assertTrue(optimized.allocationCount() > 0);

        MasterPlanOntologySession beforeConfirm =
                sessionStore.require(created.sessionId(), WorkspaceResolver.currentWorkspaceId());
        int rcaCount = beforeConfirm.graph().resourceCapacityAssignmentsById().size();
        assertTrue(rcaCount > 0);

        MasterPlanSessionConfirmResultDto result = service.confirm(created.sessionId());

        assertNotNull(result);
        assertEquals(created.sessionId(), result.sessionId());
        assertFalse(result.planVersionId().isBlank());
        assertEquals(rcaCount, result.allocationCount());

        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        String revisionId = OntRevisionHeadEntity.findHead(
                        workspaceId, PlanVersionEntRcaOccupancy.planScope(result.planVersionId()))
                .map(h -> h.revisionId)
                .orElseThrow();
        assertEquals(
                rcaCount,
                OntResourceCapacityAssignmentEntity.forRevision(workspaceId, revisionId).size());
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
        if (planVersion.score == null) {
            planVersion.score = "0hard/0soft";
            planVersion.solveDurationMs = 1L;
        }

        if (SalesOrderLineEntity.findByKey("SO-OTD-CONFIRM-001", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-OTD-CONFIRM-001";
            line.salesOrderLineNo = 1;
            line.productCode = PRODUCT_CODE;
            line.orderQty = new BigDecimal("120");
            line.dueDate = planningStart.plusDays(10);
            line.priority = 5;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-CONFIRM-001";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("120");
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
