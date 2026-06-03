package com.plantops.scenario;

import com.plantops.solver.masterplan.OrderAllocation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MasterPlanParallelBindingServiceTest {

    @Test
    void linkParallelSegmentPairsBindsEachSegmentIndex() {
        String res = "RES-X";
        OrderAllocation a0 = alloc("A0", "SO1", 1, "PROD-A", res, 10, 0);
        OrderAllocation a1 = alloc("A1", "SO1", 1, "PROD-A", res, 10, 1);
        OrderAllocation b0 = alloc("B0", "SO1", 1, "PROD-B", res, 10, 0);
        OrderAllocation b1 = alloc("B1", "SO1", 1, "PROD-B", res, 10, 1);
        List<OrderAllocation> line = List.of(a0, a1, b0, b1);

        int linked = MasterPlanParallelBindingService.linkParallelSegmentPairs(
                line, a0, b0, "GRP", "LINE-1", res);

        assertEquals(2, linked);
        assertEquals("GRP#S0", a0.getParallelGroupId());
        assertEquals("GRP#S0", b0.getParallelGroupId());
        assertEquals("GRP#S1", a1.getParallelGroupId());
        assertEquals("GRP#S1", b1.getParallelGroupId());
    }

    @Test
    void findLeadAllocationPicksEarliestOperationOnResource() {
        OrderAllocation laterOp = alloc("A@OP20_0#0", "SO1", 1, "PROD-A", "RES-X", 20, 0);
        OrderAllocation lead = alloc("A@OP10_0#0", "SO1", 1, "PROD-A", "RES-X", 10, 0);
        OrderAllocation otherResource = alloc("A@OP10_0#0", "SO1", 1, "PROD-A", "RES-Y", 10, 0);

        OrderAllocation found = MasterPlanParallelBindingService.findLeadAllocation(
                List.of(laterOp, otherResource, lead), "PROD-A", "RES-X");

        assertEquals("A@OP10_0#0", found.getId());
    }

    @Test
    void findLeadAllocationReturnsNullWhenProductMissing() {
        OrderAllocation lead = alloc("A@OP10_0#0", "SO1", 1, "PROD-A", "RES-X", 10, 0);
        assertNull(MasterPlanParallelBindingService.findLeadAllocation(List.of(lead), "PROD-B", "RES-X"));
    }

    private static OrderAllocation alloc(
            String id,
            String salesOrderNo,
            int salesOrderLineNo,
            String productCode,
            String resourceId,
            int opSeq,
            int segmentIndex) {
        OrderAllocation a = new OrderAllocation();
        a.setId(id);
        a.setSalesOrderNo(salesOrderNo);
        a.setSalesOrderLineNo(salesOrderLineNo);
        a.setProductCode(productCode);
        a.setResourceId(resourceId);
        a.setOperationSeq(opSeq);
        a.setSegmentIndex(segmentIndex);
        a.setWorkOrderNo("WO-" + productCode);
        a.setDueDate(LocalDate.now());
        a.setWorkOrderQuantity(BigDecimal.ONE);
        a.setDurationMinutes(45);
        return a;
    }
}
