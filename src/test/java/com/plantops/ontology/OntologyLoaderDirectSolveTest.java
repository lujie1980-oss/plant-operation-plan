package com.plantops.ontology;

import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Epic C.1 — BOM 展开需求写入组件 PISPP（直驱 MRP 装载）。
 */
@QuarkusTest
class OntologyLoaderDirectSolveTest {

    private static final String WORK_ORDER_NO = "WO-OTD-BOM-PISPP";
    private static final String SALES_ORDER_NO = "SO-OTD-BOM-PISPP";
    private static final String FG_CODE = "FG-OTD-BOM-PISPP";
    private static final String COMP_CODE = "RM-OTD-BOM-PISPP";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void bomExplodedDemandAggregatesIntoComponentPispp() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        String compPispId = OntologyIds.pispId(COMP_CODE);

        double componentDemand = graph.pispPeriodsById().values().stream()
                .filter(pispp -> compPispId.equals(pispp.getPispId()))
                .mapToDouble(ProductInStockingPointPeriod::getPlannedDemandQuantityTotal)
                .sum();

        assertTrue(componentDemand > 0,
                "component PISPP should receive BOM-exploded demand from parent work order");
    }

    @Test
    @TestTransaction
    void bomComponentPispExistsEvenWithoutMaterialMasterRow() {
        LocalDate planningStart = LocalDate.now();
        String orphanComp = "RM-OTD-BOM-ORPHAN";
        ensureFixture(planningStart, orphanComp);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        assertTrue(graph.pispsById().containsKey(OntologyIds.pispId(orphanComp)),
                "BOM component codes must be collected even without MaterialEntity row");
    }

    private void ensureFixture(LocalDate planningStart) {
        ensureFixture(planningStart, COMP_CODE);
    }

    private void ensureFixture(LocalDate planningStart, String componentCode) {
        if (MaterialEntity.findByCode(FG_CODE) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = FG_CODE;
            fg.materialName = FG_CODE;
            fg.stampWorkspace();
            fg.persist();
        }
        if (componentCode.equals(COMP_CODE) && MaterialEntity.findByCode(COMP_CODE) == null) {
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
            salesLine.orderQty = new BigDecimal("30");
            salesLine.dueDate = planningStart.plusDays(10);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = FG_CODE;
            workOrder.quantity = new BigDecimal("40");
            workOrder.needDate = planningStart.plusDays(4);
            workOrder.resourceId = "RES-OTD-BOM-PISPP";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }

        if (ProductResourceEntity.findByProductAndResource(FG_CODE, "RES-OTD-BOM-PISPP") == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = FG_CODE;
            routing.resourceId = "RES-OTD-BOM-PISPP";
            routing.operationName = "RUN";
            routing.sequenceNo = 1;
            routing.processTimeSeconds = new BigDecimal("60");
            routing.stampWorkspace();
            routing.persist();
        }

        if (BomComponentEntity.findByParent(FG_CODE).stream()
                .noneMatch(b -> componentCode.equals(b.componentProductCode))) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-OTD-PISPP";
            bom.bomVersion = "1";
            bom.finishedProductCode = FG_CODE;
            bom.parentProductCode = FG_CODE;
            bom.componentProductCode = componentCode;
            bom.componentQty = new BigDecimal("2");
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }
}
