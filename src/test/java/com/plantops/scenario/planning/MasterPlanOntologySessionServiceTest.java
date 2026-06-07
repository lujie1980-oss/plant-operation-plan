package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.CreateMasterPlanSessionRequest;
import com.plantops.api.dto.planning.MasterPlanSessionDto;
import com.plantops.api.dto.planning.MasterPlanSessionSimulateResultDto;
import com.plantops.api.dto.planning.PispPeriodSnapshotDto;
import com.plantops.api.dto.planning.SimulateMasterPlanSessionRequest;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.PlanVersionEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MasterPlanOntologySessionServiceTest {

    private static final String PLAN_VERSION_ID = "MPV-OTD-SESSION-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-SESSION-100";
    private static final String WORK_ORDER_NO = "WO-OTD-SESSION-001";

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
        assertEquals(PLAN_VERSION_ID, created.planVersionId());
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
}
