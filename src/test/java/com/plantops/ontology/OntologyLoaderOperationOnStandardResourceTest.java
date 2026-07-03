package com.plantops.ontology;

import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.OperationResourceBinding;
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
class OntologyLoaderOperationOnStandardResourceTest {

    private static final String WORK_ORDER_NO = "WO-OTD-OOSR-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-OOSR-100";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loadsMultipleResourceOptionsPerOperationSortedByPriority() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph g = loader.loadForWorkspace(planningStart);

        SupplyOrder so = g.supplyOrdersById().values().stream()
                .filter(s -> PRODUCT_CODE.equals(s.getProductCode()))
                .findFirst().orElseThrow();
        List<Operation> ops = g.operationsForSupplyOrder(so.getId());
        assertEquals(1, ops.size());

        Operation op = ops.get(0);
        List<OperationOnStandardResource> bindings = g.operationsOnStandardResourceFor(op.getId());
        assertEquals(2, bindings.size());
        assertEquals("RES-OOSR-PRIMARY", bindings.get(0).getStandardResourceId());
        assertEquals(1, bindings.get(0).getResourcePriority());
        assertEquals("RES-OOSR-ALT", bindings.get(1).getStandardResourceId());
        assertEquals(2, bindings.get(1).getResourcePriority());

        assertEquals("RES-OOSR-PRIMARY", OperationResourceBinding.primaryResourceId(g, op.getId()));
        assertEquals(
                List.of("RES-OOSR-PRIMARY", "RES-OOSR-ALT"),
                OperationResourceBinding.allowedResourceIds(g, op.getId()));
    }

    private void ensureFixture(LocalDate planningStart) {
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-OOSR-TEST";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("40");
            workOrder.needDate = planningStart.plusDays(5);
            workOrder.resourceId = "RES-OOSR-PRIMARY";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting("RES-OOSR-PRIMARY", "CUT", 1, 1, 5, "45");
        ensureRouting("RES-OOSR-ALT", "CUT", 1, 2, 8, "50");
    }

    private static void ensureRouting(
            String resourceId,
            String operationName,
            int sequenceNo,
            int resourcePriority,
            int setupTimeMinutes,
            String processTimeSeconds) {
        if (ProductResourceEntity.findByProductAndResource(PRODUCT_CODE, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = PRODUCT_CODE;
            routing.resourceId = resourceId;
            routing.operationName = operationName;
            routing.sequenceNo = sequenceNo;
            routing.resourcePriority = resourcePriority;
            routing.setupTimeMinutes = setupTimeMinutes;
            routing.processTimeSeconds = new BigDecimal(processTimeSeconds);
            routing.stampWorkspace();
            routing.persist();
        }
    }
}
