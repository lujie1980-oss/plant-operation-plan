package com.plantops.scenario.planning;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyLoader;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.persistence.entity.ProductResourceEntity;
import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.masterdata.BusinessRuleScopeService;
import com.plantops.ontology.OntologyIds;
import com.plantops.scenario.ProductRoutingSteps;
import com.plantops.scenario.WorkOrderScheduleContext;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyAllocationBuilderTest {

    private static final String WORK_ORDER_NO = "WO-OTD-ALLOC-TEST";
    private static final String PRODUCT_CODE = "FG-OTD-ALLOC-100";

    @Inject
    OntologyLoader loader;

    @Inject
    BusinessRuleScopeService businessRuleScopeService;

    @Test
    @TestTransaction
    void buildsAllocationsFromOperationsUsingProductionDurationOnly() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        SupplyOrder supplyOrder = graph.supplyOrdersById().values().stream()
                .filter(s -> PRODUCT_CODE.equals(s.getProductCode()))
                .findFirst().orElseThrow();
        List<TimeSlot> slots = List.of(
                slot("SLOT-A", 0, planningStart, "RES-OTD-OP-A", 480),
                slot("SLOT-B", 1, planningStart, "RES-OTD-OP-B", 480));

        List<OrderAllocation> ontologyAllocations = OntologyAllocationBuilder.buildForSupplyOrder(
                graph,
                supplyOrder,
                slots,
                false,
                false,
                5,
                new WorkOrderTimingBoundsContext(java.util.Map.of()),
                MasterPlanCapacityOverlay.empty());

        WorkOrderEntity wo = WorkOrderEntity.findByNo(WORK_ORDER_NO);
        List<OrderAllocation> entityAllocations = MasterPlanAllocationBuilder.buildForWorkOrder(
                wo,
                WorkOrderScheduleContext.resolve(wo),
                ProductRoutingSteps.operationsForProduct(PRODUCT_CODE),
                slots,
                false,
                false,
                businessRuleScopeService);

        assertEquals(2, ontologyAllocations.size());
        assertEquals(2, entityAllocations.size());

        OrderAllocation first = ontologyAllocations.get(0);
        assertEquals(WORK_ORDER_NO + "@OP1_0#0", first.getId());
        assertEquals(1, first.getOperationSeq());
        assertEquals(60, first.getDurationMinutes());
        assertEquals("RES-OTD-OP-A", first.getResourceId());
        assertEquals(List.of("RES-OTD-OP-A"), first.getAllowedResourceIds());
        assertFalse(first.getEligibleTimeSlots().isEmpty());

        OrderAllocation second = ontologyAllocations.get(1);
        assertEquals(30, second.getDurationMinutes());
        assertTrue(second.isLastSegment());
    }

    @Test
    @TestTransaction
    void splitsWhenProductionDurationExceedsSlotCapacity() {
        LocalDate planningStart = LocalDate.now();
        ensureLongRunFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        SupplyOrder supplyOrder = graph.supplyOrdersById().values().stream()
                .filter(s -> "FG-OTD-ALLOC-LONG".equals(s.getProductCode()))
                .findFirst().orElseThrow();
        List<TimeSlot> slots = List.of(slot("SLOT-L", 0, planningStart, "RES-LONG", 480));

        List<OrderAllocation> allocations = OntologyAllocationBuilder.buildForSupplyOrder(
                graph,
                supplyOrder,
                slots,
                true,
                false,
                5,
                new WorkOrderTimingBoundsContext(java.util.Map.of()),
                MasterPlanCapacityOverlay.empty());

        assertEquals(2, allocations.size());
        assertEquals(0, allocations.get(0).getSegmentIndex());
        assertEquals(1, allocations.get(1).getSegmentIndex());
        assertEquals(480, allocations.get(0).getDurationMinutes());
        assertEquals(120, allocations.get(1).getDurationMinutes());
    }

    @Test
    void plannedTimeProjectionWritesOperationPlannedBounds() {
        SupplyOrder supplyOrder = new SupplyOrder(
                WORK_ORDER_NO, PRODUCT_CODE, OntologyIds.pispId(PRODUCT_CODE), 60,
                LocalDate.now(), com.plantops.ontology.supply.SupplyOrderStatus.OPEN,
                com.plantops.ontology.supply.SupplyOrderType.PLANNED_PRODUCTION);
        Operation operation = new Operation(
                OntologyIds.operationId(WORK_ORDER_NO, 0), WORK_ORDER_NO, 0, "OP-A");
        operation.setRoutingSequenceNo(1);
        OntologyGraph graph = OntologyGraph.builder()
                .supplyOrder(supplyOrder)
                .operation(operation)
                .build();
        LocalDate slotDate = LocalDate.of(2026, 6, 15);

        OperationPlannedTimeProjection.apply(graph, List.of(
                new com.plantops.api.dto.MasterPlanAllocationDto(
                        WORK_ORDER_NO + "@OP1_0#0",
                        0,
                        WORK_ORDER_NO,
                        null,
                        null,
                        PRODUCT_CODE,
                        BigDecimal.valueOf(60),
                        null,
                        0,
                        "RES-OTD-OP-A",
                        0,
                        slotDate,
                        null,
                        slotDate.atTime(8, 0),
                        slotDate.atTime(9, 0),
                        60)));

        assertEquals(slotDate.atTime(8, 0), operation.getPlannedStartTotal());
        assertEquals(slotDate.atTime(9, 0), operation.getPlannedEndTotal());
    }

    private static TimeSlot slot(String id, int index, LocalDate date, String resourceId, int capacityMinutes) {
        return new TimeSlot(id, index, date, date, TimeslotGranularity.DAY, "DAY", resourceId, capacityMinutes);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (WorkOrderEntity.findByNo(WORK_ORDER_NO) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = WORK_ORDER_NO;
            workOrder.salesOrderNo = "SO-OTD-ALLOC-TEST";
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
        ensureRouting(PRODUCT_CODE, "RES-OTD-OP-A", "OP-A", 1, 10, "60");
        ensureRouting(PRODUCT_CODE, "RES-OTD-OP-B", "OP-B", 2, 0, "30");
    }

    private void ensureLongRunFixture(LocalDate planningStart) {
        String woNo = "WO-OTD-ALLOC-LONG";
        String product = "FG-OTD-ALLOC-LONG";
        if (WorkOrderEntity.findByNo(woNo) == null) {
            WorkOrderEntity workOrder = new WorkOrderEntity();
            workOrder.workOrderNo = woNo;
            workOrder.productCode = product;
            workOrder.quantity = new BigDecimal("600");
            workOrder.needDate = planningStart.plusDays(5);
            workOrder.resourceId = "RES-LONG";
            workOrder.sequenceNo = WorkOrderEntity.nextSequenceNo();
            workOrder.sourceType = WorkOrderEntity.SOURCE_MRP;
            workOrder.stampWorkspace();
            workOrder.persist();
        }
        ensureRouting(product, "RES-LONG", "RUN", 1, 0, "60");
    }

    private static void ensureRouting(
            String productCode,
            String resourceId,
            String operationName,
            int sequenceNo,
            int setupTimeMinutes,
            String processTimeSeconds) {
        if (ProductResourceEntity.findByProductAndResource(productCode, resourceId) == null) {
            ProductResourceEntity routing = new ProductResourceEntity();
            routing.productCode = productCode;
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
