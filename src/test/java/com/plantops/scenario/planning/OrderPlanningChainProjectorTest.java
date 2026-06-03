package com.plantops.scenario.planning;

import com.plantops.api.dto.FulfillmentChainNodeDto;
import com.plantops.api.dto.FulfillmentPegEdgeDto;
import com.plantops.api.dto.OrderFulfillmentChainDto;
import com.plantops.api.dto.planning.MasterPlanPlanningDiagnosticsDto;
import com.plantops.api.dto.planning.OrderPlanningChainDto;
import com.plantops.api.dto.planning.OrderPlanningChainNodeDto;
import com.plantops.scenario.planning.diagnostics.PlanningDiagnosticCodes;
import com.plantops.solver.masterplan.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderPlanningChainProjectorTest {

    @Test
    void workOrderNodeUsesEligibleSlotDateRange() {
        OrderAllocation alloc = allocation(
                "WO-A@OP10_0#0", "WO-A", "SO1", 1, "PROD-A", "RES-1", 10);
        alloc.setEligibleTimeSlots(List.of(
                slot("S1", "RES-1", LocalDate.of(2026, 6, 1)),
                slot("S2", "RES-1", LocalDate.of(2026, 6, 5))));

        OrderFulfillmentChainDto topology = topologyWithWorkOrder("WO-A", "n-wo-a");
        MasterPlanPlanningContext mpCtx = minimalContext(List.of(alloc));

        OrderPlanningChainDto result = OrderPlanningChainProjector.project(
                topology, mpCtx, null, List.of("WO-A"));

        OrderPlanningChainNodeDto woNode = result.nodes().stream()
                .filter(n -> "WORK_ORDER".equals(n.nodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals(LocalDate.of(2026, 6, 1), woNode.windowStart());
        assertEquals(LocalDate.of(2026, 6, 5), woNode.windowEnd());
        assertEquals("OK", woNode.status());
    }

    @Test
    void blockedWhenNoEligibleSlots() {
        OrderAllocation alloc = allocation(
                "WO-A@OP10_0#0", "WO-A", "SO1", 1, "PROD-A", "RES-1", 10);
        alloc.setEligibleTimeSlots(List.of());

        OrderFulfillmentChainDto topology = topologyWithWorkOrder("WO-A", "n-wo-a");
        MasterPlanPlanningContext mpCtx = minimalContext(List.of(alloc));

        OrderPlanningChainDto result = OrderPlanningChainProjector.project(
                topology, mpCtx, null, List.of("WO-A"));

        OrderPlanningChainNodeDto woNode = result.nodes().stream()
                .filter(n -> "WORK_ORDER".equals(n.nodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals("BLOCKED", woNode.status());
        assertTrue(woNode.planningSignals().stream()
                .anyMatch(s -> PlanningDiagnosticCodes.ALLOC_NO_RESOURCE_SLOTS.equals(s.reasonCode())));
    }

    @Test
    void blockedWhenWorkOrderNotInContext() {
        OrderFulfillmentChainDto topology = topologyWithWorkOrder("WO-MISSING", "n-wo-m");
        MasterPlanPlanningContext mpCtx = minimalContext(List.of());

        OrderPlanningChainDto result = OrderPlanningChainProjector.project(
                topology, mpCtx, null, List.of("WO-MISSING"));

        OrderPlanningChainNodeDto woNode = result.nodes().stream()
                .filter(n -> "WORK_ORDER".equals(n.nodeType()))
                .findFirst()
                .orElseThrow();
        assertEquals("BLOCKED", woNode.status());
        assertEquals("BLOCKED", result.overallStatus());
    }

    private static OrderFulfillmentChainDto topologyWithWorkOrder(String workOrderNo, String nodeId) {
        FulfillmentChainNodeDto so = new FulfillmentChainNodeDto(
                "n-so",
                "SALES_ORDER",
                "SALES_ORDER",
                "订单",
                "OK",
                0,
                "PROD-FG",
                BigDecimal.ONE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(7),
                Map.of(),
                List.of());
        FulfillmentChainNodeDto wo = new FulfillmentChainNodeDto(
                nodeId,
                "WORK_ORDER",
                "WORK_ORDER",
                "工单 · " + workOrderNo,
                "PLANNED",
                1,
                "PROD-FG",
                BigDecimal.ONE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                Map.of("workOrderNo", workOrderNo),
                List.of());
        FulfillmentPegEdgeDto edge = new FulfillmentPegEdgeDto(nodeId, "n-so", "WO_PEG", "工单满足");
        return new OrderFulfillmentChainDto(
                "SO1",
                1,
                "PROD-FG",
                LocalDate.now().plusDays(14),
                null,
                "ON_TRACK",
                "KITTING_OK",
                List.of(so, wo),
                List.of(edge),
                List.of());
    }

    private static MasterPlanPlanningContext minimalContext(List<OrderAllocation> allocations) {
        return new MasterPlanPlanningContext(
                LocalDate.of(2026, 6, 1),
                MasterPlanCapacityStrategy.UNCONSTRAINED,
                new MasterPlanObjectiveSettings(),
                MasterPlanCapacityOverlay.empty(),
                List.of(),
                allocations,
                new MaterialFeasibilityContext(Map.of()),
                List.of(),
                List.of(),
                WorkOrderTimingBoundsContext.empty(),
                new MasterPlanPlanningDiagnosticsDto(
                        LocalDateTime.now(),
                        "UNCONSTRAINED",
                        false,
                        "snap-test",
                        Map.of(),
                        List.of(),
                        false),
                null);
    }

    private static OrderAllocation allocation(
            String id,
            String workOrderNo,
            String salesOrderNo,
            int lineNo,
            String productCode,
            String resourceId,
            int opSeq) {
        OrderAllocation a = new OrderAllocation();
        a.setId(id);
        a.setWorkOrderNo(workOrderNo);
        a.setSalesOrderNo(salesOrderNo);
        a.setSalesOrderLineNo(lineNo);
        a.setProductCode(productCode);
        a.setResourceId(resourceId);
        a.setOperationSeq(opSeq);
        a.setOperationName("工序" + opSeq);
        a.setDurationMinutes(60);
        return a;
    }

    private static TimeSlot slot(String id, String resourceId, LocalDate date) {
        return new TimeSlot(id, 0, date, "DAY", resourceId, 480);
    }
}
