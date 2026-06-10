package com.plantops.ontology;

import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class OntologyLoaderOperationTest {

    private static final String WORK_ORDER_NO = "WO-OTD-OP-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-OP-100";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void buildsOperationChainPerSupplyOrder() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph g = loader.loadForWorkspace(planningStart);

        SupplyOrder so = g.supplyOrdersById().values().stream()
                .filter(s -> PRODUCT_CODE.equals(s.getProductCode()))
                .findFirst().orElseThrow();
        List<Operation> ops = g.operationsForSupplyOrder(so.getId());
        assertEquals(2, ops.size());

        Operation first = ops.get(0);
        assertEquals(0, first.getSequenceNr());
        assertEquals("OP-A", first.getOperationName());
        // 10 setup + 60s/unit * 60 units / 60 = 70 minutes
        assertEquals(70, first.getProductionTimeMinutes(), 1e-6);

        Operation second = ops.get(1);
        assertEquals(1, second.getSequenceNr());
        assertEquals("OP-B", second.getOperationName());
        // 0 setup + 30s/unit * 60 units / 60 = 30 minutes
        assertEquals(30, second.getProductionTimeMinutes(), 1e-6);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-OP-TEST";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("60");
            workOrder.needDate = planningStart.plusDays(3);
            workOrder.resourceId = "RES-OTD-OP-A";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting("RES-OTD-OP-A", "OP-A", 1, 10, "60");
        ensureRouting("RES-OTD-OP-B", "OP-B", 2, 0, "30");
    }

    private static void ensureRouting(
            String resourceId, String operationName, int sequenceNo, int setupTimeMinutes, String processTimeSeconds) {
        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.setupTimeMinutes = setupTimeMinutes;
            routing.processTimeSeconds = new BigDecimal(processTimeSeconds);
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
