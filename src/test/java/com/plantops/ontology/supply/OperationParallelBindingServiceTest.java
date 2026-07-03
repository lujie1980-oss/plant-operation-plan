package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.persistence.entity.ParallelOperationRuleEntity;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.scenario.planning.OntologyAllocationBuilder;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.OrderAllocation;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class OperationParallelBindingServiceTest {

    private static final String SALES_ORDER_NO = "SO-OTD-PARALLEL";
    private static final int SALES_ORDER_LINE_NO = 1;
    private static final String LINE_ID = "LINE-OTD-PARALLEL";
    private static final String RESOURCE_ID = "RES-OTD-PARALLEL";
    private static final String RESOURCE_ID_SECOND = "RES-OTD-PAR-2";
    private static final String PRODUCT_A = "FG-OTD-PAR-A";
    private static final String PRODUCT_B = "FG-OTD-PAR-B";
    private static final String WO_A = "WO-OTD-PAR-A";
    private static final String WO_B = "WO-OTD-PAR-B";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loaderAssignsParallelGroupToLeadOperationsOnSameOrderLine() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ParallelOperationRuleEntity rule = ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        Operation leadA = graph.operationsForSupplyOrder(WO_A).get(0);
        Operation leadB = graph.operationsForSupplyOrder(WO_B).get(0);
        Operation secondA = graph.operationsForSupplyOrder(WO_A).get(1);

        String expectedBase = "MPP-" + rule.id + "-" + SALES_ORDER_NO + "#" + SALES_ORDER_LINE_NO + "-" + LINE_ID;
        assertEquals(expectedBase, leadA.getParallelGroupId());
        assertEquals(expectedBase, leadB.getParallelGroupId());
        assertNull(secondA.getParallelGroupId());
    }

    @Test
    @TestTransaction
    void allocationBuilderPropagatesSegmentSuffixOnParallelGroupId() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        ParallelOperationRuleEntity rule = ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        List<TimeSlot> slots = List.of(
                slot("SLOT-PAR", 0, planningStart, RESOURCE_ID, 480),
                slot("SLOT-PAR-2", 1, planningStart, RESOURCE_ID_SECOND, 480));

        List<OrderAllocation> allocationsA = OntologyAllocationBuilder.buildForSupplyOrder(
                graph,
                graph.supplyOrder(WO_A),
                slots,
                false,
                false,
                5,
                new WorkOrderTimingBoundsContext(java.util.Map.of()),
                MasterPlanCapacityOverlay.empty());
        List<OrderAllocation> allocationsB = OntologyAllocationBuilder.buildForSupplyOrder(
                graph,
                graph.supplyOrder(WO_B),
                slots,
                false,
                false,
                5,
                new WorkOrderTimingBoundsContext(java.util.Map.of()),
                MasterPlanCapacityOverlay.empty());

        String expectedGroup = "MPP-" + rule.id + "-" + SALES_ORDER_NO + "#" + SALES_ORDER_LINE_NO
                + "-" + LINE_ID + "#S0";
        assertEquals(expectedGroup, allocationsA.get(0).getParallelGroupId());
        assertEquals(expectedGroup, allocationsB.get(0).getParallelGroupId());
        assertNull(allocationsA.get(1).getParallelGroupId());
    }

    @Test
    void findLeadOperationPicksEarliestRoutingSequenceOnResource() {
        Operation later = operation("OP-LATE", 20);
        Operation lead = operation("OP-LEAD", 10);
        OntologyGraph graph = graphWithOperations(later, lead);

        Operation found = OperationParallelBindingService.findLeadOperation(
                List.of(
                        new OperationParallelBindingService.OperationCandidate(later, WO_A, PRODUCT_A),
                        new OperationParallelBindingService.OperationCandidate(lead, WO_A, PRODUCT_A)),
                PRODUCT_A,
                RESOURCE_ID,
                graph);

        assertNotNull(found);
        assertEquals(10, found.getRoutingSequenceNo());
    }

    private static Operation operation(String name, int routingSeq) {
        Operation operation = new Operation("OP-" + routingSeq, WO_A, 0, name);
        operation.setRoutingSequenceNo(routingSeq);
        return operation;
    }

    private static OntologyGraph graphWithOperations(Operation... operations) {
        var builder = OntologyGraph.builder();
        for (Operation operation : operations) {
            builder.operation(operation);
            builder.operationOnStandardResource(new OperationOnStandardResource(
                    "OOSR-" + operation.getId(),
                    operation.getId(),
                    RESOURCE_ID,
                    1,
                    0,
                    60.0));
        }
        return builder.build();
    }

    private ParallelOperationRuleEntity ensureFixture(LocalDate planningStart) {
        ensureProductionLine();
        ensureWorkOrder(WO_A, PRODUCT_A, planningStart);
        ensureWorkOrder(WO_B, PRODUCT_B, planningStart);
        ensureRouting(PRODUCT_A, RESOURCE_ID, "OP-A1", 1);
        ensureRouting(PRODUCT_A, RESOURCE_ID_SECOND, "OP-A2", 2);
        ensureRouting(PRODUCT_B, RESOURCE_ID, "OP-B1", 1);
        return ensureParallelRule();
    }

    private static void ensureProductionLine() {
        if (ProductionLineEntity.findByLineId(LINE_ID) == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = LINE_ID;
            line.areaId = "AREA-OTD-PARALLEL";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 100;
            line.stampWorkspace();
            line.persist();
        }
    }

    private static ParallelOperationRuleEntity ensureParallelRule() {
        ParallelOperationRuleEntity existing = ParallelOperationRuleEntity.findEntry(LINE_ID, PRODUCT_A, PRODUCT_B);
        if (existing != null) {
            return existing;
        }
        ParallelOperationRuleEntity rule = new ParallelOperationRuleEntity();
        rule.lineId = LINE_ID;
        rule.firstProductCode = PRODUCT_A;
        rule.secondProductCode = PRODUCT_B;
        rule.stampWorkspace();
        rule.persist();
        return rule;
    }

    private static void ensureWorkOrder(String workOrderNo, String productCode, LocalDate planningStart) {
        if (WorkOrderEntity.findByNo(workOrderNo) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = workOrderNo;
            workOrder.salesOrderNo = SALES_ORDER_NO;
            workOrder.salesOrderLineNo = SALES_ORDER_LINE_NO;
            workOrder.productCode = productCode;
            workOrder.quantity = new BigDecimal("10");
            workOrder.needDate = planningStart.plusDays(5);
            workOrder.resourceId = RESOURCE_ID;
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
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

    private static TimeSlot slot(String id, int index, LocalDate date, String resourceId, int capacityMinutes) {
        return new TimeSlot(id, index, date, date, TimeslotGranularity.DAY, "DAY", resourceId, capacityMinutes);
    }
}
