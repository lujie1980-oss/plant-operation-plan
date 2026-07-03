package com.plantops.ontology;

import com.plantops.api.dto.CapacityAnalysisDto;
import com.plantops.api.dto.MaterialRequirementReportDto;
import com.plantops.ontology.persistence.OntologyLegacyMutationCoordinator;
import com.plantops.ontology.persistence.OntologyPersistencePort;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.CapacityService;
import com.plantops.scenario.OntologyMaterialPlanningService;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sprint 6B: read-only scenario services load via {@link WorkspaceAuthoritativeOntologyGraphService}.
 */
@QuarkusTest
class AuthoritativeOntologyReadPathIntegrationTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-READ-6B";
    private static final String PRODUCT_CODE = "FG-OTD-READ-6B";
    private static final String WORK_ORDER_NO = "WO-OTD-READ-6B";
    private static final String RESOURCE_ID = "RES-OTD-READ-6B";

    @Inject
    OntologyMaterialPlanningService materialPlanningService;

    @Inject
    CapacityService capacityService;

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyPersistencePort ontologyPersistence;

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    OntologyLegacyMutationCoordinator legacyMutationCoordinator;

    @Test
    @TestTransaction
    void materialBalanceAndCapacityUseAuthoritativeGraph() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);
        legacyMutationCoordinator.afterWorkOrdersChanged(WorkspaceResolver.currentWorkspaceId());
        refreshOntWorkspaceHead();

        MaterialRequirementReportDto balance =
                materialPlanningService.balance(PLAN_VERSION_ID);
        assertNotNull(balance);
        assertFalse(balance.materials().isEmpty());

        CapacityAnalysisDto capacity = capacityService.analyzeForMasterPlan(PLAN_VERSION_ID);
        assertNotNull(capacity);
        assertNotNull(capacity.loadBuckets());
    }

    private void refreshOntWorkspaceHead() {
        String workspaceId = WorkspaceResolver.currentWorkspaceId();
        ontologyPersistence.importCommittedP0(
                workspaceId, ontologyLoader.loadForPlanVersion(PLAN_VERSION_ID));
        authoritativeOntologyGraph.invalidateWorkspace(workspaceId);
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

        if (SalesOrderLineEntity.findByKey("SO-READ-6B", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-READ-6B";
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
            workOrder.salesOrderNo = "SO-READ-6B";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("40");
            workOrder.needDate = planningStart.plusDays(8);
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
