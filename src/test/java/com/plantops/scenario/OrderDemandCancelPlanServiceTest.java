package com.plantops.scenario;

import com.plantops.persistence.entity.WorkOrderEntity;
import com.plantops.persistence.entity.WorkOrderPeggingEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderDemandCancelPlanServiceTest {

    @Test
    void shouldRetainWhenOtherOrderPegged() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;

        List<WorkOrderPeggingEntity> pegs = List.of(
                peg("SO-1", 10),
                peg("SO-2", 20));

        assertTrue(OrderDemandCancelPlanService.shouldRetainWorkOrder(pegs, "SO-1", 10, wo));
    }

    @Test
    void shouldNotRetainWhenOnlyThisOrder() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.dispatchStatus = WorkOrderService.DISPATCH_PENDING;

        List<WorkOrderPeggingEntity> pegs = List.of(peg("SO-1", 10));

        assertFalse(OrderDemandCancelPlanService.shouldRetainWorkOrder(pegs, "SO-1", 10, wo));
    }

    @Test
    void shouldRetainWhenDispatchedEvenIfExclusive() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.dispatchStatus = WorkOrderService.DISPATCH_DISPATCHED;

        List<WorkOrderPeggingEntity> pegs = List.of(peg("SO-1", 10));

        assertTrue(OrderDemandCancelPlanService.shouldRetainWorkOrder(pegs, "SO-1", 10, wo));
    }

    private static WorkOrderPeggingEntity peg(String salesOrderNo, int lineNo) {
        WorkOrderPeggingEntity peg = new WorkOrderPeggingEntity();
        peg.salesOrderNo = salesOrderNo;
        peg.salesOrderLineNo = lineNo;
        peg.peggedQty = BigDecimal.ONE;
        peg.needDate = LocalDate.of(2026, 6, 1);
        return peg;
    }
}
