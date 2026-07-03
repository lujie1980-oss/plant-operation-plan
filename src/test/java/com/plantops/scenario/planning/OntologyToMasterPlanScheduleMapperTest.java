package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.planning.MasterPlanSolveProfile;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.SalesOrderLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyToMasterPlanScheduleMapperTest {

    private static final String WORK_ORDER_NO = "WO-OTD-SCHEDULE-MAP";
    private static final String PRODUCT_CODE = "FG-OTD-SCHED-100";

    @Inject
    OntologyLoader ontologyLoader;

    @Inject
    OntologyToMasterPlanScheduleMapper scheduleMapper;

    @Test
    @TestTransaction
    void toScheduleBuildsNonEmptyProblemFromGraph() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ensureFixture(planningStart);

        OntologyGraph graph = ontologyLoader.loadForWorkspace(planningStart);
        MasterPlanSchedule schedule = scheduleMapper.toSchedule(
                graph, MasterPlanSolveProfile.defaults(planningStart));

        assertNotNull(schedule);
        assertFalse(schedule.getTimeSlotRange().isEmpty());
        assertFalse(schedule.getOrderAllocations().isEmpty());
        assertNotNull(schedule.getMaterialFeasibility());
        assertTrue(schedule.getOrderAllocations().stream()
                .anyMatch(a -> WORK_ORDER_NO.equals(a.getWorkOrderNo())));
        assertFalse(schedule.getOperationPrecedenceEdges().isEmpty());

        OrderAllocation first = schedule.getOrderAllocations().stream()
                .filter(a -> WORK_ORDER_NO.equals(a.getWorkOrderNo()))
                .findFirst()
                .orElseThrow();
        assertNotNull(first.getSalesOrderNo());
        assertTrue(first.getEligibleTimeSlots() != null && !first.getEligibleTimeSlots().isEmpty());
    }

    private void ensureFixture(LocalDate planningStart) {
        if (SalesOrderLineEntity.findByKey("SO-OTD-SCHED", 1) == null) {
            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.salesOrderNo = "SO-OTD-SCHED";
            line.salesOrderLineNo = 1;
            line.productCode = PRODUCT_CODE;
            line.orderQty = new BigDecimal("30");
            line.dueDate = planningStart.plusDays(7);
            line.priority = 5;
            line.status = "OPEN";
            line.stampWorkspace();
            line.persist();
        }
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-SCHED";
            workOrder.salesOrderLineNo = 1;
            workOrder.productCode = PRODUCT_CODE;
            workOrder.quantity = new BigDecimal("30");
            workOrder.needDate = planningStart.plusDays(7);
            workOrder.resourceId = "RES-OTD-SCHED-A";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting(PRODUCT_CODE, "RES-OTD-SCHED-A", "OP-A", 1);
        ensureRouting(PRODUCT_CODE, "RES-OTD-SCHED-B", "OP-B", 2);
    }

    private static void ensureRouting(
            String productCode,
            String resourceId,
            String operationName,
            int sequenceNo) {
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
