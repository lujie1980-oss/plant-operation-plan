package com.plantops.ontology;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.scenario.planning.MasterPlanOntologySessionService;
import com.plantops.scenario.planning.MasterPlanOntologySessionStore;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandbox;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxService;
import com.plantops.scenario.planning.delivery.DeliveryPlanningSandboxStore;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * AC-13 / ADR-07：ENT-SES 与 ENT-SBX 共享同一权威 ENT-OG。
 */
@QuarkusTest
class AuthoritativeOntologyGraphAc13Test {

    private static final String PLAN_VERSION_ID = "MPV-AC13-AUTH-OG";
    private static final String PRODUCT_CODE = "FG-AC13-AUTH-100";
    private static final String WORK_ORDER_NO = "WO-AC13-AUTH-001";
    private static final String RESOURCE_ID = "RES-AC13-AUTH-S1";
    private static final String ALLOCATION_ID = "ALLOC-AC13-AUTH-001";
    private static final String SALES_ORDER_NO = "SO-AC13-AUTH-001";
    private static final String DELIVERY_ID = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
    private static final String SRP_ID = OntologyIds.srpId(RESOURCE_ID, 0);

    @Inject
    WorkspaceAuthoritativeOntologyGraphService authoritativeOntologyGraph;

    @Inject
    MasterPlanOntologySessionService sessionService;

    @Inject
    MasterPlanOntologySessionStore sessionStore;

    @Inject
    DeliveryPlanningSandboxService sandboxService;

    @Inject
    DeliveryPlanningSandboxStore sandboxStore;

    @BeforeEach
    void clearAuthoritativeCache() {
        authoritativeOntologyGraph.invalidateWorkspace(WorkspaceResolver.currentWorkspaceId());
        DeliveryPlanningSandbox existing = sandboxStore.findByDelivery(
                WorkspaceResolver.currentWorkspaceId(), DELIVERY_ID);
        if (existing != null) {
            sandboxStore.remove(existing.sandboxId());
        }
    }

    @Test
    @TestTransaction
    void sessionAndSandboxShareSameGraphInstance() {
        ensureFixtureData();

        MasterPlanSessionDto sessionDto = sessionService.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        DeliveryPlanningSandbox sandbox = sandboxService.getOrCreate(DELIVERY_ID, PLAN_VERSION_ID);

        var session = sessionStore.require(sessionDto.sessionId(), WorkspaceResolver.currentWorkspaceId());
        assertSame(session.graph(), sandbox.graph());
    }

    @Test
    @TestTransaction
    void simulateSrpOnSessionIsVisibleInSandboxGraph() {
        ensureFixtureData();

        MasterPlanSessionDto sessionDto = sessionService.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        DeliveryPlanningSandbox sandbox = sandboxService.getOrCreate(DELIVERY_ID, PLAN_VERSION_ID);

        sessionService.simulate(
                sessionDto.sessionId(),
                new SimulateMasterPlanSessionRequest(
                        "SRP",
                        SRP_ID,
                        null,
                        "reservedCapacity",
                        150.0,
                        null));

        var srp = sandbox.graph().srp(SRP_ID);
        assertEquals(150.0, srp.getReservedCapacity(), 1e-6);
        assertEquals(330.0, srp.getFreeCapacity(), 1e-6);
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

        SalesOrderLineEntity orderLine = SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1);
        if (orderLine == null) {
            orderLine = new SalesOrderLineEntity();
            orderLine.salesOrderNo = SALES_ORDER_NO;
            orderLine.salesOrderLineNo = 1;
            orderLine.productCode = PRODUCT_CODE;
            orderLine.orderQty = new BigDecimal("100");
            orderLine.dueDate = LocalDate.now().plusDays(14);
            orderLine.status = "OPEN";
            orderLine.stampWorkspace();
            orderLine.persist();
        }

        WorkOrderEntity workOrder = WorkOrderEntity.findByNo(WORK_ORDER_NO);
        if (workOrder == null) {
            workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("100");
            workOrder.resourceId = RESOURCE_ID;
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
            allocation.salesOrderNo = SALES_ORDER_NO;
            allocation.salesOrderLineNo = 1;
            allocation.resourceId = RESOURCE_ID;
            allocation.slotIndex = 0;
            allocation.slotDate = LocalDate.now();
            allocation.shiftId = "DAY";
            allocation.durationMinutes = 90;
            allocation.stampWorkspace();
            allocation.persist();
        }

        ProductionLineEntity line = ProductionLineEntity.findByLineId("LINE-AC13-AUTH-01");
        if (line == null) {
            line = new ProductionLineEntity();
            line.lineId = "LINE-AC13-AUTH-01";
            line.areaId = "AREA-AC13-AUTH";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 480;
            line.stampWorkspace();
            line.persist();
        }

        ResourceCalendarEntity calendar = ResourceCalendarEntity.findForResource(RESOURCE_ID).stream()
                .filter(c -> LocalDate.now().equals(c.calendarDate))
                .findFirst()
                .orElse(null);
        if (calendar == null) {
            calendar = new ResourceCalendarEntity();
            calendar.resourceId = RESOURCE_ID;
            calendar.shiftId = "DAY";
            calendar.calendarDate = LocalDate.now();
            calendar.availableCapacityMinutes = 480;
            calendar.unavailableCapacityMinutes = 0;
            calendar.stampWorkspace();
            calendar.persist();
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
}
