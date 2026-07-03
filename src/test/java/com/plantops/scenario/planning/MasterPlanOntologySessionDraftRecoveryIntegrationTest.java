package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.ontology.persistence.OntologySessionPersistenceService;
import com.plantops.ontology.persistence.entity.OntSessionEntity;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-PERS-02 (Session API): simulate persists DRAFT; after in-memory eviction, restore ≡ last write.
 */
@QuarkusTest
class MasterPlanOntologySessionDraftRecoveryIntegrationTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-PERS-6C";
    private static final String PRODUCT_CODE = "FG-OTD-PERS-6C";
    private static final String WORK_ORDER_NO = "WO-OTD-PERS-6C";
    private static final String RESOURCE_ID = "RES-OTD-PERS-6C";

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

    @Inject
    OntologySessionPersistenceService sessionPersistence;

    @Test
    @TestTransaction
    void simulateSurvivesProcessRestartViaRestore() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead();

        MasterPlanSessionDto created = sessionService.create(
                new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        String pispId = OntologyIds.pispId(PRODUCT_CODE);
        String pisppId = OntologyIds.pisppId(pispId, 0);

        sessionService.simulate(
                created.sessionId(),
                new SimulateMasterPlanSessionRequest(pisppId, "plannedSupplyTotal", 180.0));

        List<PispPeriodSnapshotDto> afterSimulate =
                sessionService.listPispPeriods(created.sessionId(), pispId);
        PispPeriodSnapshotDto target = afterSimulate.stream()
                .filter(p -> pisppId.equals(p.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(180.0, target.plannedSupplyTotal(), 1e-9);

        assertTrue(sessionPersistence.currentChangeSeq(
                WorkspaceResolver.currentWorkspaceId(), created.sessionId()) > 0);

        sessionStore.remove(created.sessionId());

        MasterPlanSessionDto restored = sessionService.restoreSessionFromPersistence(created.sessionId());
        assertEquals(created.sessionId(), restored.sessionId());
        assertEquals(PLAN_VERSION_ID, restored.basePlanVersionId());

        List<PispPeriodSnapshotDto> afterRestore =
                sessionService.listPispPeriods(restored.sessionId(), pispId);
        PispPeriodSnapshotDto restoredPeriod = afterRestore.stream()
                .filter(p -> pisppId.equals(p.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(180.0, restoredPeriod.plannedSupplyTotal(), 1e-9);

        OntSessionEntity row = OntSessionEntity.findSession(
                        WorkspaceResolver.currentWorkspaceId(), created.sessionId())
                .orElseThrow();
        assertTrue(row.solveProfileJson != null);
        assertEquals(PLAN_VERSION_ID, row.solveProfileJson.get(
                OntologySessionPersistenceService.SOLVE_PROFILE_PLAN_VERSION_KEY));
    }

    private void refreshOntWorkspaceHead() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        ontologyPersistence.importCommittedP0(
                workspaceId, ontologyLoader.loadForPlanVersion(PLAN_VERSION_ID));
        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (PlanVersionEntity.findByVersionId(PLAN_VERSION_ID) == null) {
            PlanVersionEntity planVersion = new PlanVersionEntity();
            planVersion.planVersionId = PLAN_VERSION_ID;
            planVersion.planType = "MASTER_PLAN";
            planVersion.planGeneratedTs = LocalDateTime.now();
            planVersion.stampWorkspace();
            planVersion.persist();
        }

        if (SalesOrderLineEntity.findByKey("SO-PERS-6C", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-PERS-6C";
            line.salesOrderLineNo = 1;
            line.productCode = PRODUCT_CODE;
            line.orderQty = new BigDecimal("40");
            line.dueDate = planningStart.plusDays(10);
            line.priority = 5;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-PERS-6C";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("40");
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
    }
}
