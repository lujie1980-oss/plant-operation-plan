package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionConfirmResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class MasterPlanOntologyConfirmServiceTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-CONFIRM-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-CONFIRM-100";
    private static final String WORK_ORDER_NO = "WO-OTD-CONFIRM-001";
    private static final String ALLOCATION_ID = "ALLOC-OTD-CONFIRM-001";

    @Inject
    MasterPlanOntologySessionService service;

    @BeforeEach
    @Transactional
    void ensureFixtureData() {
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

        WorkOrderEntity workOrder = WorkOrderEntity.findByNo(WORK_ORDER_NO);
        if (workOrder == null) {
            workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-CONFIRM-001";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("120");
            workOrder.resourceId = "RES-OTD-CONFIRM-01";
            workOrder.sequenceNo = 1;
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        MasterPlanAllocationEntity allocation = MasterPlanAllocationEntity.find(
                        "workspaceId = ?1 and planVersionId = ?2 and allocationId = ?3",
                        WorkspaceResolver.currentWorkspaceId(),
                        PLAN_VERSION_ID,
                        ALLOCATION_ID)
                .firstResult();
        if (allocation == null) {
            allocation = new MasterPlanAllocationEntity();
            allocation.planVersionId = PLAN_VERSION_ID;
            allocation.allocationId = ALLOCATION_ID;
            allocation.workOrderNo = WORK_ORDER_NO;
            allocation.productCode = PRODUCT_CODE;
            allocation.salesOrderNo = "SO-OTD-CONFIRM-001";
            allocation.salesOrderLineNo = 1;
            allocation.resourceId = "RES-OTD-CONFIRM-01";
            allocation.slotIndex = 0;
            allocation.slotDate = LocalDate.now();
            allocation.shiftId = "DAY";
            allocation.durationMinutes = 480;
            allocation.stampWorkspace();
            allocation.persist();
        }

        InventoryEntity inventory = InventoryEntity.find(
                        "workspaceId = ?1 and productCode = ?2",
                        WorkspaceResolver.currentWorkspaceId(),
                        PRODUCT_CODE)
                .firstResult();
        if (inventory == null) {
            inventory = new InventoryEntity();
            inventory.stockingPointCode = OntologyIds.DEFAULT_FG;
            inventory.productCode = PRODUCT_CODE;
            inventory.onhandQty = new BigDecimal("40");
            inventory.reservedQty = BigDecimal.ZERO;
            inventory.qualityHoldQty = BigDecimal.ZERO;
            inventory.stampWorkspace();
            inventory.persist();
        }
    }

    @Test
    void confirmPersistsAllocationsAndReturnsPlanVersionId() throws Exception {
        MasterPlanSessionDto created = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        service.optimize(created.sessionId());

        MasterPlanSessionConfirmResultDto result = service.confirm(created.sessionId());

        assertNotNull(result);
        assertEquals(created.sessionId(), result.sessionId());
        assertFalse(result.planVersionId().isBlank());
        assertEquals(
                (int) MasterPlanAllocationEntity.count("planVersionId = ?1", result.planVersionId()),
                result.allocationCount());
        assertFalse(result.allocationCount() < 0);
    }
}
