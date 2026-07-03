package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionOptimizeResultDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.PispSummaryDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanOntologySessionServiceTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-SESSION-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-SESSION-100";
    private static final String WORK_ORDER_NO = "WO-OTD-SESSION-001";
    private static final String ALLOCATION_ID = "ALLOC-OTD-SESSION-001";

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
            workOrder.salesOrderNo = "SO-OTD-SESSION-001";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("120");
            workOrder.resourceId = "RES-OTD-SESSION-01";
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
            allocation.salesOrderNo = "SO-OTD-SESSION-001";
            allocation.salesOrderLineNo = 1;
            allocation.resourceId = "RES-OTD-SESSION-01";
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
    void createAndSimulateSupplyChangeUpdatesPispChain() {
        MasterPlanSessionDto created = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        assertNotNull(created.sessionId());
        assertTrue(created.sessionId().startsWith("MOS-"));
        assertEquals(PLAN_VERSION_ID, created.basePlanVersionId());
        assertTrue(created.pispCount() > 0);

        String pispId = OntologyIds.pispId(PRODUCT_CODE);
        String p0Id = OntologyIds.pisppId(pispId, 0);
        String p1Id = OntologyIds.pisppId(pispId, 1);

        MasterPlanSessionSimulateResultDto result = service.simulate(
                created.sessionId(),
                new SimulateMasterPlanSessionRequest(p0Id, "plannedSupplyTotal", 200.0));

        assertTrue(result.recalculatedPeriodIds().size() >= 2);

        Map<String, PispPeriodSnapshotDto> snapshots = new HashMap<>();
        for (PispPeriodSnapshotDto snapshot : result.snapshots()) {
            snapshots.put(snapshot.id(), snapshot);
        }

        PispPeriodSnapshotDto p0 = snapshots.get(p0Id);
        PispPeriodSnapshotDto p1 = snapshots.get(p1Id);
        assertNotNull(p0);
        assertNotNull(p1);
        assertEquals(200.0, p0.plannedSupplyTotal(), 1e-6);
        assertEquals(p0.plannedInventoryLevel(), p1.onHand(), 1e-6);
    }

    @Test
    void optimizeProjectsAllocationsToAffectedSnapshots() throws Exception {
        MasterPlanSessionDto created = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));

        MasterPlanSessionOptimizeResultDto result = service.optimize(created.sessionId());

        assertNotNull(result);
        assertEquals(created.sessionId(), result.sessionId());
        assertTrue(result.allocationCount() >= 0);
        if (result.allocationCount() > 0) {
            assertNotNull(result.affectedSnapshots());
            assertTrue(!result.affectedSnapshots().isEmpty());
            String pispId = OntologyIds.pispId(PRODUCT_CODE);
            assertTrue(result.affectedSnapshots().stream()
                    .anyMatch(snapshot -> pispId.equals(snapshot.pispId())
                            && snapshot.plannedSupplyTotal() > 0));
        }
    }

    @Test
    void listPispsAndPeriodsReturnsOrderedSnapshots() {
        MasterPlanSessionDto created = service.create(new CreateMasterPlanSessionRequest(PLAN_VERSION_ID, null));
        String pispId = OntologyIds.pispId(PRODUCT_CODE);

        List<PispSummaryDto> pisps = service.listPisps(created.sessionId());
        assertTrue(pisps.stream().anyMatch(p -> pispId.equals(p.pispId()) && PRODUCT_CODE.equals(p.productCode())));

        List<PispPeriodSnapshotDto> periods = service.listPispPeriods(created.sessionId(), pispId);
        assertEquals(28, periods.size());
        for (int idx = 0; idx < periods.size(); idx++) {
            PispPeriodSnapshotDto snapshot = periods.get(idx);
            assertEquals(OntologyIds.pisppId(pispId, idx), snapshot.id());
            assertEquals(pispId, snapshot.pispId());
        }
    }
}
