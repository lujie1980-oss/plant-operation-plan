package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.WorkspaceAuthoritativeOntologyGraphService;
import com.plantops.ontology.persistence.OntologyPersistencePort;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanOntologySessionDirectOptimizeTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-DIRECT-OPT";
    private static final String PRODUCT_CODE = "FG-OTD-DIRECT-OPT";
    private static final String WORK_ORDER_NO = "WO-OTD-DIRECT-OPT";
    private static final String RESOURCE_ID = "RES-OTD-DIRECT-OPT";

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
    void directOptimizeUsesSessionGraphAndConfirmPersistsLastSolution() throws Exception {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        refreshOntWorkspaceHead();

        MasterPlanSessionDto created = sessionService.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        String pispId = OntologyIds.pispId(PRODUCT_CODE);
        String p0Id = OntologyIds.pisppId(pispId, 0);

        sessionService.simulate(
                created.sessionId(),
                new SimulateMasterPlanSessionRequest(p0Id, "plannedSupplyTotal", 180.0));

        MasterPlanSessionOptimizeResultDto optimized = sessionService.optimize(created.sessionId());
        assertNotNull(optimized.score());
        assertTrue(optimized.allocationCount() > 0);

        MasterPlanOntologySession stored = sessionStore.require(created.sessionId(), WorkspaceResolver.currentWorkspaceId());
        assertNotNull(stored.lastOptimizerResult());
        assertTrue(stored.lastOptimizerResult().persistAllocations().size() > 0);
        int rcaCount = stored.graph().resourceCapacityAssignmentsById().size();
        assertTrue(rcaCount > 0);

        MasterPlanSessionConfirmResultDto confirmed = sessionService.confirm(created.sessionId());
        assertNotNull(confirmed.planVersionId());
        assertEquals(rcaCount, confirmed.allocationCount());
        assertTrue(confirmed.allocationCount() > 0);
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

        if (SalesOrderLineEntity.findByKey("SO-OTD-DIRECT", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-OTD-DIRECT";
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
            workOrder.salesOrderNo = "SO-OTD-DIRECT";
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
