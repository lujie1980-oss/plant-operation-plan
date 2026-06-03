package com.plantops.solver.masterplan;

import com.plantops.scenario.ChangeoverRuleIndex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MasterPlanSlotChangeoverTest {

    @Test
    void sameProduct_noPenalty() {
        OrderAllocation a = allocation("P1", "OP-A");
        OrderAllocation b = allocation("P1", "OP-A");
        assertEquals(0, MasterPlanSlotChangeover.switchPenaltyMinutes(a, b, new ChangeoverRuleIndex(java.util.List.of())));
    }

    @Test
    void differentProduct_usesFallbackWhenNoRules() {
        OrderAllocation a = allocation("P1", "OP-A");
        OrderAllocation b = allocation("P2", "OP-A");
        assertEquals(
                MasterPlanSlotChangeover.FALLBACK_SWITCH_MINUTES,
                MasterPlanSlotChangeover.switchPenaltyMinutes(a, b, new ChangeoverRuleIndex(java.util.List.of())));
    }

    @Test
    void resolveOperationName_prefersSharedOperation() {
        OrderAllocation a = allocation("P1", "OP-A");
        OrderAllocation b = allocation("P2", "OP-A");
        assertEquals("OP-A", MasterPlanSlotChangeover.resolveOperationName(a, b));
    }

    @Test
    void resolveOperationName_nullWhenBothMissing() {
        OrderAllocation a = allocation("P1", null);
        OrderAllocation b = allocation("P2", null);
        assertNull(MasterPlanSlotChangeover.resolveOperationName(a, b));
    }

    private static OrderAllocation allocation(String productCode, String operationName) {
        OrderAllocation a = new OrderAllocation();
        a.setProductCode(productCode);
        a.setOperationName(operationName);
        return a;
    }
}
