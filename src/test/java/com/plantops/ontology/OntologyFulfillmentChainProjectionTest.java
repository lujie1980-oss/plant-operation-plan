package com.plantops.ontology;

import com.plantops.api.dto.CustomerOrderLineDeliveryListItemDto;
import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.scenario.OntologyFulfillmentService;
import com.plantops.persistence.entity.BomComponentEntity;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyFulfillmentChainProjectionTest {

    private static final String SALES_ORDER_NO = "SO-SC-FF-TEST";
    private static final String PARENT_WO = "WO-SC-FF-PARENT";
    private static final String CHILD_WO = "WO-SC-FF-CHILD";
    private static final String FG_CODE = "FG-SC-FF-100";
    private static final String COMP_CODE = "RM-SC-FF-200";

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Test
    @TestTransaction
    void listsCustomerOrderLineDeliveries() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        List<CustomerOrderLineDeliveryListItemDto> deliveries =
                ontologyFulfillmentService.listDeliveries(null);
        CustomerOrderLineDeliveryListItemDto row = deliveries.stream()
                .filter(d -> SALES_ORDER_NO.equals(d.salesOrderNo()))
                .findFirst()
                .orElseThrow();

        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        assertEquals(coldId, row.deliveryId());
        assertEquals(FG_CODE, row.productCode());
        assertEquals(40.0, row.deliveryQty(), 1e-6);
    }

    @Test
    @TestTransaction
    void projectsSupplyOrderFulfillmentChainForDelivery() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        OrderFulfillmentChainDto chain = ontologyFulfillmentService.fulfillmentChain(coldId, null);

        assertEquals(coldId, chain.deliveryId());
        assertEquals(SALES_ORDER_NO, chain.salesOrderNo());
        assertFalse(chain.nodes().isEmpty());

        FulfillmentChainNodeDto root = chain.nodes().stream()
                .filter(n -> "SALES_ORDER".equals(n.nodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, root.depth());

        FulfillmentChainNodeDto parentSupply = chain.nodes().stream()
                .filter(n -> "SUPPLY_ORDER".equals(n.nodeType())
                        && PARENT_WO.equals(n.attributes().get("supplyOrderId")))
                .findFirst()
                .orElseThrow();
        assertTrue(parentSupply.depth() >= 1);
        assertNotNull(parentSupply.operations());
    }

    @Test
    @TestTransaction
    void projectsUpstreamChainForChildWorkOrder() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OrderFulfillmentChainDto chain =
                ontologyFulfillmentService.supplyOrderUpstreamChain(CHILD_WO, null);

        assertFalse(chain.nodes().isEmpty());
        FulfillmentChainNodeDto root = chain.nodes().stream()
                .filter(n -> CHILD_WO.equals(n.attributes().get("supplyOrderId")))
                .findFirst()
                .orElseThrow();
        assertEquals(0, root.depth());
    }

    @Test
    @TestTransaction
    void projectsUpstreamChainForParentWorkOrder() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OrderFulfillmentChainDto chain =
                ontologyFulfillmentService.supplyOrderUpstreamChain(PARENT_WO, null);

        assertTrue(chain.nodes().stream().anyMatch(n -> "SUPPLY_ORDER".equals(n.nodeType())
                && CHILD_WO.equals(n.attributes().get("supplyOrderId"))));
    }

    @Test
    @TestTransaction
    void projectsDownstreamChainForChildWorkOrder() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureParentChildFixture(planningStart);

        OrderFulfillmentChainDto chain =
                ontologyFulfillmentService.supplyOrderDownstreamChain(CHILD_WO, null);

        assertFalse(chain.nodes().isEmpty());
        FulfillmentChainNodeDto root = chain.nodes().stream()
                .filter(n -> CHILD_WO.equals(n.attributes().get("supplyOrderId")))
                .findFirst()
                .orElseThrow();
        assertEquals(0, root.depth());
        assertTrue(chain.nodes().stream().anyMatch(n -> "SUPPLY_ORDER".equals(n.nodeType())
                && PARENT_WO.equals(n.attributes().get("supplyOrderId"))));
        assertTrue(chain.nodes().stream().anyMatch(n -> "SALES_ORDER".equals(n.nodeType())));
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
        ensureRouting(COMP_CODE, "RES-SC-FF-CHILD", "RM-OP-A", 1);

        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-SC-FF-PROJ";
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
