package com.plantops.ontology;

import com.plantops.persistence.entity.MaterialEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.SystemParameterEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyLoaderSupplyDemandTest {

    private static final String WORK_ORDER_NO = "WO-SUPPLY-DEMAND-TEST";
    private static final String SALES_ORDER_NO = "SO-SUPPLY-DEMAND-TEST";
    private static final String PRODUCT_CODE = "FG-SUPPLY-DEMAND-TEST";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void supplyAndDemandAggregateIntoPispByPeriod() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        boolean anySupply = graph.pispPeriodsById().values().stream()
                .anyMatch(p -> p.getPlannedSupplyTotal() > 0);
        boolean anyDemand = graph.pispPeriodsById().values().stream()
                .anyMatch(p -> p.getPlannedDemandQuantityTotal() > 0);

        assertTrue(anySupply, "expected some PISPP supply from work orders");
        assertTrue(anyDemand, "expected some PISPP demand from sales orders");
    }

    @Test
    @TestTransaction
    void mixedBucketSpecChangesPeriodCount() {
        setSystemParameter("ontology_period_sequence", "2x1d,1x1w");
        OntologyGraph g = loader.loadForWorkspace(LocalDate.now());
        assertEquals(3, g.periodsOrdered().size());
    }

    private static void setSystemParameter(String paramId, String value) {
        SystemParameterEntity row = SystemParameterEntity.findByParamId(paramId);
        if (row == null) {
            row = new SystemParameterEntity();
            row.paramId = paramId;
            row.stampWorkspace();
        }
        row.paramValue = value;
        row.persist();
    }

    private void ensureFixture(LocalDate planningStart) {
        if (MaterialEntity.findByCode(PRODUCT_CODE) == null) {
            MaterialEntity material = new MaterialEntity();
            material.materialCode = PRODUCT_CODE;
            material.materialName = PRODUCT_CODE;
            material.stampWorkspace();
            material.persist();
        }

        if (SalesOrderLineEntity.findByKey(SALES_ORDER_NO, 1) == null) {
            SalesOrderLineEntity salesLine = new SalesOrderLineEntity();
            salesLine.salesOrderNo = SALES_ORDER_NO;
            salesLine.salesOrderLineNo = 1;
            salesLine.productCode = PRODUCT_CODE;
            salesLine.orderQty = new BigDecimal("50");
            salesLine.dueDate = planningStart.plusDays(5);
            salesLine.status = "OPEN";
            salesLine.stampWorkspace();
            salesLine.persist();
        }

        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("75");
            workOrder.needDate = planningStart.plusDays(3);
            workOrder.resourceId = "RES-SUPPLY-DEMAND-TEST";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
    }
}
