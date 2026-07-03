package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OperationTimingBridgeServiceTest {

    private static final String WORK_ORDER_NO = "WO-OTD-TIMING-BRIDGE";
    private static final String PRODUCT_CODE = "FG-OTD-TIMING-100";
    private static final String COMPONENT_CODE = "RM-OTD-TIMING-01";

    @Inject
    OntologyLoader loader;

    @Inject
    OperationTimingBridgeService timingBridgeService;

    @Test
    @TestTransaction
    void loaderAppliesWorkOrderTimingToOperations() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        List<Operation> ops = graph.operationsForSupplyOrder(WORK_ORDER_NO);
        assertEquals(2, ops.size());

        LocalDateTime todayStart = OperationTimeAnchor.horizonStart(LocalDate.now());
        assertEquals(todayStart, ops.get(0).getEarliestPossibleStartOwn());
        assertEquals(todayStart, ops.get(1).getEarliestPossibleStartOwn());
        assertEquals(todayStart, ops.get(0).getEarliestPossibleStartTotal());
        assertFalse(ops.get(1).getEarliestPossibleStartTotal().isBefore(ops.get(0).getEarliestPossibleEndTotal()));
        assertNotNull(ops.get(1).getLatestDesiredEnd());
        assertFalse(ops.get(0).isInfeasible());
    }

    @Test
    @TestTransaction
    void criticalShortageShiftsEarliestOwnStart() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureShortageFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        Operation first = graph.operationsForSupplyOrder(WORK_ORDER_NO).get(0);

        LocalDateTime todayStart = OperationTimeAnchor.horizonStart(LocalDate.now());
        assertTrue(first.getEarliestPossibleStartOwn().isAfter(todayStart));
        assertEquals(first.getEarliestPossibleStartOwn(), first.getEarliestPossibleStartTotal());
    }

    @Test
    @TestTransaction
    void serviceReappliesWindowAfterManualNeedDateChange() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        SupplyOrder supplyOrder = graph.supplyOrder(WORK_ORDER_NO);
        supplyOrder.setNeedDate(LocalDate.of(2026, 6, 25));
        timingBridgeService.applyForSupplyOrder(graph, WORK_ORDER_NO, planningStart);

        Operation last = graph.operationsForSupplyOrder(WORK_ORDER_NO).get(1);
        assertEquals(LocalDate.of(2026, 6, 25), last.getLatestDesiredEnd().toLocalDate());
    }

    private void ensureFixture(LocalDate planningStart) {
        ensureMaterial(PRODUCT_CODE, "FG");
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("10");
            workOrder.needDate = planningStart.plusDays(10);
            workOrder.resourceId = "RES-TIMING-A";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting("RES-TIMING-A", "OP-1", 1, "60");
        ensureRouting("RES-TIMING-B", "OP-2", 2, "30");
    }

    private void ensureShortageFixture(LocalDate planningStart) {
        ensureFixture(planningStart);
        ensureMaterial(COMPONENT_CODE, "RM");
        if (BomComponentEntity.findByParent(PRODUCT_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-OTD-TIMING";
            bom.bomVersion = "1";
            bom.finishedProductCode = PRODUCT_CODE;
            bom.parentProductCode = PRODUCT_CODE;
            bom.componentProductCode = COMPONENT_CODE;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
        InventoryEntity.delete("productCode = ?1", COMPONENT_CODE);
    }

    private static void ensureMaterial(String code, String type) {
        if (MaterialEntity.findByCode(code) == null) {
            MaterialEntity material = new MaterialEntity();
            material.materialCode = code;
            material.materialName = code;
            material.materialType = type;
            material.stampWorkspace();
            material.persist();
        }
    }

    private static void ensureInventory(String productCode, String qty) {
        if (InventoryEntity.findByProduct(productCode).isEmpty()) {
            InventoryEntity inventory = new InventoryEntity();
            inventory.productCode = productCode;
            inventory.onhandQty = new BigDecimal(qty);
            inventory.stampWorkspace();
            inventory.persist();
        }
    }

    private static void ensureRouting(String resourceId, String operationName, int sequenceNo, String processSeconds) {
        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.setupTimeMinutes = 0;
            routing.processTimeSeconds = new BigDecimal(processSeconds);
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
