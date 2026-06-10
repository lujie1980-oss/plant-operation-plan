package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
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

@QuarkusTest
class MasterPlanOntologySessionSrpTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-SRP-SESSION-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-SRP-SESSION-100";
    private static final String WORK_ORDER_NO = "WO-OTD-SRP-SESSION-001";
    private static final String RESOURCE_ID = "RES-OTD-SRP-S1";

    @Inject
    MasterPlanOntologySessionService service;

    @Inject
    MasterPlanOntologySessionStore store;

    @Test
    @TestTransaction
    void srpRulesActiveInSession() {
        ensureFixtureData();

        MasterPlanSessionDto session = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        MasterPlanOntologySession stored = store.require(session.sessionId(), WorkspaceResolver.currentWorkspaceId());
        StandardResourcePeriod srp = stored.graph().srp(OntologyIds.srpId(RESOURCE_ID, 0));
        assertNotNull(srp);
        assertEquals(480, srp.getAvailableCapacity(), 1e-6);
        stored.rolEngine().applyPropertyChange(srp, "reservedCapacity", 120.0);
        assertEquals(360, srp.getFreeCapacity(), 1e-6); // 480 - 120, derived via merged registry
    }

    private void ensureFixtureData() {
        PlanVersionEntity planVersion = PlanVersionEntity.findByVersionId(PLAN_VERSION_ID);
        if (planVersion == null) {
            planVersion = new PlanVersionEntity();
            planVersion.planVersionId = PLAN_VERSION_ID;
            planVersion.planType = "MASTER_PLAN";
            planVersion.planGeneratedTs = LocalDateTime.now();
            planVersion.stampWorkspace();
            planVersion.persist();
        }

        WorkOrderEntity workOrder = WorkOrderEntity.findByNo(WORK_ORDER_NO);
        if (workOrder == null) {
            workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-SRP-SESSION-001";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("120");
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = 1;
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
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

        if (ProductionLineEntity.findByLineId("LINE-OTD-SRP-S1") == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = "LINE-OTD-SRP-S1";
            line.areaId = "AREA-OTD-SRP-S1";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 100;
            line.stampWorkspace();
            line.persist();
        }

        if (ResourceCalendarEntity.findForResource(RESOURCE_ID).isEmpty()) {
            ResourceCalendarEntity shift = new ResourceCalendarEntity();
            shift.resourceId = RESOURCE_ID;
            shift.shiftId = "SHIFT-1";
            shift.calendarDate = LocalDate.now();
            shift.availableCapacityMinutes = 480;
            shift.unavailableCapacityMinutes = 0;
            shift.stampWorkspace();
            shift.persist();
        }
    }
}
