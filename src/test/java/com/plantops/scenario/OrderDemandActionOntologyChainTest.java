package com.plantops.scenario;

import com.plantops.api.dto.demand.OrderDemandActionRequest;
import com.plantops.api.dto.demand.OrderDemandActionResult;
import com.plantops.ontology.OntologyIds;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderBomDependencyEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OrderDemandActionOntologyChainTest {

    private static final String SALES_ORDER_NO = "SO-SC-ACTION-ONT";
    private static final String PARENT_WO = "WO-SC-ACTION-PARENT";
    private static final String CHILD_WO = "WO-SC-ACTION-CHILD";
    private static final String FG_CODE = "FG-SC-ACTION-100";
    private static final String COMP_CODE = "RM-SC-ACTION-200";

    @Inject
    OrderDemandActionService orderDemandActionService;

    @Test
    @TestTransaction
    void buildUpstreamChainReturnsOntologyProjectedFulfillmentChain() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);

        OrderDemandActionResult result = orderDemandActionService.execute(
                SALES_ORDER_NO,
                1,
                OrderDemandAction.INFINITE_PLAN_JIT,
                new OrderDemandActionRequest(null, null, null, null));

        assertNotNull(result.fulfillmentChain());
        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        assertEquals(coldId, result.fulfillmentChain().deliveryId());
        assertTrue(result.fulfillmentChain().nodes().stream()
                .anyMatch(n -> "SUPPLY_ORDER".equals(n.nodeType())));
    }

    @Test
    @TestTransaction
    void cancelPlanRemovesExclusiveWorkOrdersAndReturnsScopedChain() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);
        ensurePegging();

        OrderDemandActionResult result = orderDemandActionService.execute(
                SALES_ORDER_NO,
                1,
                OrderDemandAction.CANCEL_PLAN,
                new OrderDemandActionRequest(null, null, null, null));

        assertNotNull(result.fulfillmentChain());
        assertNull(WorkOrderEntity.findByNo(PARENT_WO));
        assertNull(WorkOrderEntity.findByNo(CHILD_WO));
        assertTrue(result.fulfillmentChain().nodes().stream()
                .noneMatch(n -> "SUPPLY_ORDER".equals(n.nodeType())));
        assertTrue(result.message().contains("已取消计划"));
    }

    private void ensurePegging() {
        if (WorkOrderPeggingEntity.findByOrderLine(SALES_ORDER_NO, 1).isEmpty()) {
            WorkOrderPeggingEntity parentPeg = new WorkOrderPeggingEntity();
            parentPeg.workOrderNo = PARENT_WO;
            parentPeg.salesOrderNo = SALES_ORDER_NO;
            parentPeg.salesOrderLineNo = 1;
            parentPeg.finishedProductCode = FG_CODE;
            parentPeg.peggedQty = new BigDecimal("40");
            parentPeg.needDate = LocalDate.of(2026, 6, 17);
            parentPeg.stampWorkspace();
            parentPeg.persist();

            WorkOrderPeggingEntity childPeg = new WorkOrderPeggingEntity();
            childPeg.workOrderNo = CHILD_WO;
            childPeg.salesOrderNo = SALES_ORDER_NO;
            childPeg.salesOrderLineNo = 1;
            childPeg.finishedProductCode = FG_CODE;
            childPeg.peggedQty = new BigDecimal("60");
            childPeg.needDate = LocalDate.of(2026, 6, 14);
            childPeg.stampWorkspace();
            childPeg.persist();
        }
    }

    private void ensureFixture(LocalDate planningStart) {
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
            child.resourceId = "RES-SC-ACTION-CHILD";
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
            parent.resourceId = "RES-SC-ACTION-PARENT";
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
        ensureRouting(FG_CODE, "RES-SC-ACTION-PARENT", "FF-OP-A", 1);
        ensureRouting(COMP_CODE, "RES-SC-ACTION-CHILD", "RM-OP-A", 1);
        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-SC-ACTION";
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

    private static void ensureRouting(String productCode, String resourceId, String opName, int seq) {
        if (ProductResourceEntity.findByProductAndResource(productCode, resourceId) != null) {
            return;
        }
        ProductResourceEntity pr = new ProductResourceEntity();
        pr.productCode = productCode;
        pr.resourceId = resourceId;
        pr.operationName = opName;
        pr.sequenceNo = seq;
        pr.processTimeSeconds = new BigDecimal("3600");
        pr.stampWorkspace();
        pr.persist();
    }
}
