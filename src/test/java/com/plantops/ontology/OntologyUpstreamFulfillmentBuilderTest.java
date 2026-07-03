package com.plantops.ontology;

import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import com.plantops.scenario.OntologyFulfillmentService;
import com.plantops.scenario.OrderDemandAction;
import com.plantops.scenario.OrderDemandActionService;
import com.plantops.api.dto.demand.OrderDemandActionRequest;
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
class OntologyUpstreamFulfillmentBuilderTest {

    private static final String SALES_ORDER_NO = "SO-UP-BUILD-TEST";
    private static final String FG_CODE = "FG-UP-BUILD-100";
    private static final String COMP_CODE = "RM-UP-BUILD-200";

    @Inject
    OntologyLoader loader;

    @Inject
    OntologyFulfillmentService ontologyFulfillmentService;

    @Inject
    OrderDemandActionService orderDemandActionService;

    @Test
    @TestTransaction
    void buildsSyntheticSupplyOrdersWithJitOperationWindows() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);

        String coldId = OntologyIds.customerOrderLineDeliveryId(SALES_ORDER_NO, 1, 0);
        OntologyGraph graph = loader.buildUpstreamFulfillmentGraph(coldId, planningStart);

        String rootDemandId = OntologyIds.demandFromCustomerDeliveryId(coldId);
        Fulfillment fgPeg = graph.fulfillmentsForDemand(rootDemandId).stream()
                .filter(ff -> ff.getType() == FulfillmentType.WORK_ORDER_PEG)
                .findFirst()
                .orElseThrow();
        String fgSoId = graph.supply(fgPeg.getSupplyId()).getSupplyOrderId();
        SupplyOrder fgSo = graph.supplyOrder(fgSoId);
        assertNotNull(fgSo);
        assertEquals(40.0, fgSo.getQuantity(), 1e-6);
        assertNotNull(WorkOrderEntity.findByNo(fgSoId));

        var fgOps = graph.operationsForSupplyOrder(fgSoId);
        assertFalse(fgOps.isEmpty());
        Operation lastFgOp = fgOps.get(fgOps.size() - 1);
        assertNotNull(lastFgOp.getLatestDesiredEnd());
        assertNotNull(lastFgOp.getLatestDesiredStart());

        String bomDemandId = OntologyIds.demandFromBomId(fgSoId, COMP_CODE);
        assertNotNull(graph.demand(bomDemandId));
        assertEquals(DemandSourceType.BOM_COMPONENT, graph.demand(bomDemandId).getSourceType());

        Fulfillment childPeg = graph.fulfillmentsForDemand(bomDemandId).stream()
                .filter(ff -> ff.getType() == FulfillmentType.WORK_ORDER_PEG)
                .findFirst()
                .orElseThrow();
        String childSoId = graph.supply(childPeg.getSupplyId()).getSupplyOrderId();
        assertNotNull(graph.supplyOrder(childSoId));
        assertNotNull(WorkOrderEntity.findByNo(childSoId));
        assertFalse(graph.bomDependenciesForParent(fgSoId).isEmpty());
        assertFalse(WorkOrderPeggingEntity.findByOrderLine(SALES_ORDER_NO, 1).isEmpty());
    }

    @Test
    @TestTransaction
    void buildUpstreamActionPersistsMrpWorkOrders() {
        LocalDate planningStart = LocalDate.of(2026, 6, 10);
        ensureFixture(planningStart);
        long woBefore = WorkOrderEntity.listInWorkspace().size();

        var result = orderDemandActionService.execute(
                SALES_ORDER_NO,
                1,
                OrderDemandAction.BUILD_UPSTREAM_CHAIN,
                new OrderDemandActionRequest(null, null, null, null));

        assertTrue(WorkOrderEntity.listInWorkspace().size() > woBefore);
        assertNotNull(result.fulfillmentChain());
        assertTrue(result.fulfillmentChain().nodes().stream()
                .anyMatch(n -> "SUPPLY_ORDER".equals(n.nodeType())));
        assertTrue(result.message().contains("MRP 工单"));
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
        ensureRouting(FG_CODE, "RES-UP-BUILD-PARENT", "FF-OP-A", 1);
        ensureRouting(COMP_CODE, "RES-UP-BUILD-CHILD", "RM-OP-A", 1);
        if (BomComponentEntity.findByParent(FG_CODE).isEmpty()) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-UP-BUILD";
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
