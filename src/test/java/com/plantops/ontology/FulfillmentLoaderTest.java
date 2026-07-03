package com.plantops.ontology;

import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.supply.Supply;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.InventoryEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class FulfillmentLoaderTest {

    private static final String PARENT_WO = "WO-SC-FF-PARENT";
    private static final String CHILD_WO = "WO-SC-FF-CHILD";
    private static final String SALES_ORDER_NO = "SO-SC-FF-TEST";
    private static final String FG_CODE = "FG-SC-FF-100";
    private static final String COMP_CODE = "RM-SC-FF-200";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void bomComponentDemandPeggedToChildSupplyOrder() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        String bomDemandId = OntologyIds.demandFromBomId(PARENT_WO, COMP_CODE);
        Demand bomDemand = graph.demand(bomDemandId);
        assertNotNull(bomDemand);
        assertEquals(DemandSourceType.BOM_COMPONENT, bomDemand.getSourceType());

        Fulfillment woPeg = graph.fulfillmentsForDemand(bomDemandId).stream()
                .filter(ff -> ff.getType() == FulfillmentType.WORK_ORDER_PEG)
                .findFirst()
                .orElseThrow();
        assertEquals(OntologyIds.supplyId(CHILD_WO, 0), woPeg.getSupplyId());
        assertEquals(60.0, woPeg.getQuantity(), 1e-6);

        Supply childSupply = graph.supply(woPeg.getSupplyId());
        assertNotNull(childSupply);
        assertEquals(CHILD_WO, childSupply.getSupplyOrderId());
        assertEquals(COMP_CODE, childSupply.getProductCode());
    }

    @Test
    @TestTransaction
    void customerDemandUsesInventoryBeforeWorkOrder() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);
        ensureInventory(FG_CODE, 15);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        String demandId = OntologyIds.demandFromCustomerDeliveryId(coldId);
        var fulfillments = graph.fulfillmentsForDemand(demandId);
        assertFalse(fulfillments.isEmpty());

        Fulfillment invPeg = fulfillments.stream()
                .filter(ff -> ff.getType() == FulfillmentType.INVENTORY_PEG)
                .findFirst()
                .orElseThrow();
        assertEquals(15.0, invPeg.getQuantity(), 1e-6);
        assertEquals(OntologyIds.inventorySupplyId(FG_CODE), invPeg.getSupplyId());

        Fulfillment woPeg = fulfillments.stream()
                .filter(ff -> ff.getType() == FulfillmentType.WORK_ORDER_PEG)
                .findFirst()
                .orElseThrow();
        assertEquals(OntologyIds.supplyId(PARENT_WO, 0), woPeg.getSupplyId());
        assertEquals(25.0, woPeg.getQuantity(), 1e-6);
    }

    @Test
    @TestTransaction
    void forecastDemandShortageWhenNoSupply() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureMaterialsOnly();

        if (com.plantops.persistence.entity.ForecastDemandEntity.findByForecastId("FC-SC-FF-ONLY") == null) {
            var fc = new com.plantops.persistence.entity.ForecastDemandEntity();
            fc.forecastId = "FC-SC-FF-ONLY";
            fc.productCode = "FG-SC-FF-ORPHAN";
            fc.quantity = new BigDecimal("10");
            fc.needDate = planningStart.plusDays(10);
            fc.stampWorkspace();
            fc.persist();
        }
        if (MaterialEntity.findByCode("FG-SC-FF-ORPHAN") == null) {
            MaterialEntity m = new MaterialEntity();
            m.materialCode = "FG-SC-FF-ORPHAN";
            m.materialName = "FG-SC-FF-ORPHAN";
            m.stampWorkspace();
            m.persist();
        }

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        String demandId = OntologyIds.demandFromForecastId(OntologyIds.forecastDemandId("FC-SC-FF-ONLY"));
        Fulfillment shortage = graph.fulfillmentsForDemand(demandId).stream()
                .filter(ff -> ff.getType() == FulfillmentType.SHORTAGE_PEG)
                .findFirst()
                .orElseThrow();
        assertEquals(10.0, shortage.getQuantity(), 1e-6);
        assertEquals(OntologyIds.shortageSupplyId("FG-SC-FF-ORPHAN"), shortage.getSupplyId());
    }

    private void ensureMaterialsOnly() {
        if (MaterialEntity.findByCode(FG_CODE) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = FG_CODE;
            fg.materialName = FG_CODE;
            fg.stampWorkspace();
            fg.persist();
        }
    }

    private void ensureParentChildFixture(LocalDate planningStart) {
        ensureMaterialsOnly();
        if (MaterialEntity.findByCode(COMP_CODE) == null) {
            MaterialEntity comp = new MaterialEntity();
            comp.materialCode = COMP_CODE;
            comp.materialName = COMP_CODE;
            comp.stampWorkspace();
            comp.persist();
        }

        if (SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1) == null) {
            SalesOrderLineEntity salesLine = new SalesOrderLineEntity();
            salesLine.salesOrderNo = SALES_ORDER_NO;
            salesLine.salesOrderLineNo = 1;
            salesLine.productCode = FG_CODE;
            salesLine.orderQty = new BigDecimal("40");
            salesLine.dueDate = planningStart.plusDays(7);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }

        if (WorkOrderEntity.findByNo(CHILD_WO) == null) {
            WorkOrderEntity child = new WorkOrderEntity();
            child.workOrderNo = CHILD_WO;
            child.salesOrderNo = SALES_ORDER_NO;
            child.salesOrderLineNo = 1;
            child.productCode = COMP_CODE;
            child.quantity = new BigDecimal("60");
            child.needDate = planningStart.plusDays(4);
            child.resourceId = "RES-SC-FF-CHILD";
            child.parentWorkOrderNo = PARENT_WO;
            child.sequenceNo = WorkOrderEntity.nextSequenceNo();
            child.sourceType = WorkOrderEntity.SOURCE_MRP;
            child.stampWorkspace();
            child.persist();
        }

        if (WorkOrderEntity.findByNo(PARENT_WO) == null) {
            WorkOrderEntity parent = new WorkOrderEntity();
            parent.workOrderNo = PARENT_WO;
            parent.salesOrderNo = SALES_ORDER_NO;
            parent.salesOrderLineNo = 1;
            parent.productCode = FG_CODE;
            parent.quantity = new BigDecimal("60");
            parent.needDate = planningStart.plusDays(5);
            parent.resourceId = "RES-SC-FF-PARENT";
            parent.sequenceNo = WorkOrderEntity.nextSequenceNo();
            parent.sourceType = WorkOrderEntity.SOURCE_MRP;
            parent.stampWorkspace();
            parent.persist();
        }

        if (WorkOrderBomDependencyEntity.findByParent(PARENT_WO).isEmpty()) {
            WorkOrderBomDependencyEntity dep = new WorkOrderBomDependencyEntity();
            dep.parentWorkOrderNo = PARENT_WO;
            dep.childWorkOrderNo = CHILD_WO;
            dep.stampWorkspace();
            dep.persist();
        }

        ensureRouting(FG_CODE, "RES-SC-FF-PARENT", "FF-OP-A", 1);
        ensureRouting(COMP_CODE, "RES-SC-FF-CHILD", "FF-OP-C", 1);

        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-SC-FF";
            bom.bomVersion = "1";
            bom.finishedProductCode = FG_CODE;
            bom.parentProductCode = FG_CODE;
            bom.componentProductCode = COMP_CODE;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }

    private static void ensureInventory(String productCode, double qty) {
        if (InventoryEntity.findByProduct(productCode).isEmpty()) {
            InventoryEntity inv = new InventoryEntity();
            inv.stockingPointCode = "DEFAULT-FG";
            inv.productCode = productCode;
            inv.onhandQty = BigDecimal.valueOf(qty);
            inv.reservedQty = BigDecimal.ZERO;
            inv.qualityHoldQty = BigDecimal.ZERO;
            inv.stampWorkspace();
            inv.persist();
        }
    }

    private static void ensureRouting(String productCode, String resourceId, String operationName, int sequenceNo) {
        if (ProductResourceEntity.findByProductAndResource(productCode, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = productCode;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.setupTimeMinutes = 0;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
