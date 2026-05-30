package com.plantops.scenario.planning;

import com.plantops.solver.masterplan.OperationPrecedenceEdge;
import com.plantops.solver.masterplan.OrderAllocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterPlanOperationPrecedenceBuilderTest {

    @Test
    void buildsEdgeFromLastSegmentOfOpToFirstSegmentOfNextOp() {
        OrderAllocation op10a = alloc("WO-1@OP10_0#0", "WO-1", 10, 0);
        OrderAllocation op10b = alloc("WO-1@OP10_0#1", "WO-1", 10, 1);
        OrderAllocation op20 = alloc("WO-1@OP20_1#0", "WO-1", 20, 2);

        List<OperationPrecedenceEdge> edges = MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(
                List.of(op20, op10a, op10b));

        assertEquals(1, edges.size());
        assertEquals("WO-1@OP10_0#1", edges.get(0).predecessorAllocationId());
        assertEquals("WO-1@OP20_1#0", edges.get(0).successorAllocationId());
    }

    @Test
    void singleOperationWorkOrderProducesNoEdges() {
        OrderAllocation only = alloc("WO-2@OP10_0#0", "WO-2", 10, 0);
        assertTrue(MasterPlanOperationPrecedenceBuilder.buildSerialOperationEdges(List.of(only)).isEmpty());
    }

    private static OrderAllocation alloc(String id, String wo, int opSeq, int segmentIndex) {
        OrderAllocation a = new OrderAllocation();
        a.setId(id);
        a.setWorkOrderNo(wo);
        a.setOperationSeq(opSeq);
        a.setSegmentIndex(segmentIndex);
        a.setProductCode("P-" + wo);
        a.setResourceId("RES-1");
        a.setDueDate(LocalDate.now());
        a.setWorkOrderQuantity(BigDecimal.ONE);
        a.setDurationMinutes(60);
        return a;
    }
}
