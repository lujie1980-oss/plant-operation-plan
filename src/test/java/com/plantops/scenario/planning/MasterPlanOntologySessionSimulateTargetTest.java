package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.OperationSnapshotDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.SrpSnapshotDto;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanOntologySessionSimulateTargetTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-SIM-TARGET";
    private static final String PRODUCT_CODE = "FG-OTD-SIM-TARGET";
    private static final String WORK_ORDER_NO = "WO-OTD-SIM-TARGET";
    private static final String RESOURCE_ID = "RES-OTD-SIM-TARGET";

    @Inject
    MasterPlanOntologySessionService service;

    @Test
    @TestTransaction
    void simulateSrpReservedCapacityReturnsSrpSnapshot() {
        ensureFixture(LocalDate.now().plusDays(10));
        MasterPlanSessionDto session = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        String srpId = OntologyIds.srpId(RESOURCE_ID, 0);

        MasterPlanSessionSimulateResultDto result = service.simulate(
                session.sessionId(),
                new SimulateMasterPlanSessionRequest("SRP", srpId, null, "reservedCapacity", 150.0, null));

        assertTrue(result.snapshots().isEmpty());
        assertEquals(1, result.srpSnapshots().size());
        SrpSnapshotDto srp = result.srpSnapshots().get(0);
        assertEquals(150.0, srp.reservedCapacity(), 1e-6);
        assertEquals(330.0, srp.freeCapacity(), 1e-6);
    }

    @Test
    @TestTransaction
    void simulateSupplyOrderNeedDateReturnsOperationSnapshots() {
        LocalDate originalNeed = LocalDate.now().plusDays(10);
        ensureFixture(originalNeed);
        MasterPlanSessionDto session = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));

        LocalDate newNeed = originalNeed.plusDays(5);
        MasterPlanSessionSimulateResultDto result = service.simulate(
                session.sessionId(),
                new SimulateMasterPlanSessionRequest(
                        "SUPPLY_ORDER", WORK_ORDER_NO, null, "needDate", null, newNeed.toString()));

        assertTrue(result.snapshots().isEmpty());
        assertTrue(result.srpSnapshots().isEmpty());
        assertFalse(result.operationSnapshots().isEmpty());
        OperationSnapshotDto lastOp = result.operationSnapshots().get(result.operationSnapshots().size() - 1);
        assertNotNull(lastOp.latestDesiredEnd());
        assertEquals(newNeed, lastOp.latestDesiredEnd().toLocalDate());
    }

    @Test
    @TestTransaction
    void legacyPisppRequestStillWorks() {
        LocalDate need = LocalDate.now().plusDays(7);
        ensureFixture(need);
        MasterPlanSessionDto session = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        String p0Id = OntologyIds.pisppId(OntologyIds.pispId(PRODUCT_CODE), 0);

        MasterPlanSessionSimulateResultDto result = service.simulate(
                session.sessionId(),
                new SimulateMasterPlanSessionRequest(p0Id, "plannedSupplyTotal", 88.0));

        assertFalse(result.recalculatedPeriodIds().isEmpty());
        assertTrue(result.snapshots().stream()
                .anyMatch(s -> p0Id.equals(s.id()) && s.plannedSupplyTotal() == 88.0));
    }

    private void ensureFixture(LocalDate needDate) {
        if (PlanVersionEntity.findByVersionId(PLAN_VERSION_ID) == null) {
            PlanVersionEntity planVersion = new PlanVersionEntity();
            planVersion.planVersionId = PLAN_VERSION_ID;
            planVersion.planType = "MASTER_PLAN";
            planVersion.planGeneratedTs = LocalDateTime.now();
            planVersion.stampWorkspace();
            planVersion.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-SIM-TARGET";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("40");
            workOrder.needDate = needDate;
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        if (ProductionLineEntity.findByLineId("LINE-OTD-SIM-TARGET") == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = "LINE-OTD-SIM-TARGET";
            line.areaId = "AREA-OTD-SIM-TARGET";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 100;
            line.stampWorkspace();
            line.persist();
        }

        if (ResourceCalendarEntity.findForResource(RESOURCE_ID).isEmpty()) {
            ResourceCalendarEntity cal = new ResourceCalendarEntity();
            cal.resourceId = RESOURCE_ID;
            cal.shiftId = "DAY";
            cal.calendarDate = LocalDate.now();
            cal.availableCapacityMinutes = 480;
            cal.unavailableCapacityMinutes = 0;
            cal.stampWorkspace();
            cal.persist();
        }

        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, RESOURCE_ID) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = RESOURCE_ID;
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.processTimeSeconds = new BigDecimal("3600");
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
}
