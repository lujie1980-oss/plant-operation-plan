package com.plantops.ontology;

import com.plantops.ontology.fulfillment.BomDependencyDerivation;
import com.plantops.ontology.supply.BomDependency;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.testsupport.SpecRef;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@SpecRef("AC-10")
class BomDependencyDerivationTest {

    private static final String PARENT_WO = "WO-SC-BOM-PARENT";
    private static final String CHILD_WO = "WO-SC-BOM-CHILD";
    private static final String SALES_ORDER_NO = "SO-SC-BOM-TEST";
    private static final String FG_CODE = "FG-SC-BOM-100";
    private static final String COMP_CODE = "RM-SC-BOM-200";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void derivesParentChildEdgeFromBomFulfillmentChain() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        BomDependency dep = graph.bomDependency(OntologyIds.bomDependencyId(PARENT_WO, CHILD_WO));
        assertEquals(PARENT_WO, dep.getParentSupplyOrderId());
        assertEquals(CHILD_WO, dep.getChildSupplyOrderId());

        var solverEdges = BomDependencyDerivation.toSolverEdges(graph);
        assertTrue(solverEdges.contains(new BomDependencyEdge(PARENT_WO, CHILD_WO)));
    }

    @Test
    @TestTransaction
    void derivedEdgesMatchWorkOrderBomDependencyEntityForFixture() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        Set<BomDependencyEdge> derived = BomDependencyDerivation.toSolverEdges(graph).stream()
                .filter(edge -> PARENT_WO.equals(edge.parentWorkOrderNo())
                        || CHILD_WO.equals(edge.childWorkOrderNo()))
                .collect(Collectors.toSet());

        Set<BomDependencyEdge> jpa = WorkOrderBomDependencyEntity.findByParent(PARENT_WO).stream()
                .map(dep -> new BomDependencyEdge(dep.parentWorkOrderNo, dep.childWorkOrderNo))
                .collect(Collectors.toSet());

        assertEquals(jpa, derived);
        assertFalse(derived.isEmpty());
    }

    private void ensureParentChildFixture(LocalDate planningStart) {
        if (MaterialEntity.findByCode(FG_CODE) == null) {
            MaterialEntity fg = new MaterialEntity();
            fg.materialCode = FG_CODE;
            fg.materialName = FG_CODE;
            fg.stampWorkspace();
            fg.persist();
        }
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
            child.resourceId = "RES-SC-BOM-CHILD";
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
            parent.resourceId = "RES-SC-BOM-PARENT";
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

        ensureRouting(FG_CODE, "RES-SC-BOM-PARENT", "BOM-OP-A", 1);
        ensureRouting(COMP_CODE, "RES-SC-BOM-CHILD", "BOM-OP-C", 1);

        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-SC-BOM";
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
