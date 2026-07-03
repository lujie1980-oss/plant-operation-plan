package com.plantops.ontology.supply;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.solver.masterplan.WorkOrderTimingBoundsContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OperationTimingBoundsProjectionTest {

    @Test
    void usesEarliestTotalStartAcrossOperations() {
        String woNo = "WO-BOUNDS";
        SupplyOrder supplyOrder = new SupplyOrder(
                woNo, "FG-1", OntologyIds.pispId("FG-1"), 10,
                LocalDate.of(2026, 6, 15), SupplyOrderStatus.OPEN, SupplyOrderType.PLANNED_PRODUCTION);
        Operation op0 = new Operation(OntologyIds.operationId(woNo, 0), woNo, 0, "OP-0");
        op0.setEarliestPossibleStartTotal(LocalDateTime.of(2026, 6, 5, 8, 0));
        Operation op1 = new Operation(OntologyIds.operationId(woNo, 1), woNo, 1, "OP-1");
        op1.setEarliestPossibleStartTotal(LocalDateTime.of(2026, 6, 6, 8, 0));

        OntologyGraph graph = OntologyGraph.builder()
                .supplyOrder(supplyOrder)
                .operation(op0)
                .operation(op1)
                .build();

        WorkOrderTimingBoundsContext bounds = OperationTimingBoundsProjection.fromGraph(graph);
        assertNotNull(bounds.earliestStart(woNo));
        assertEquals(LocalDateTime.of(2026, 6, 5, 8, 0), bounds.earliestStart(woNo));
    }
}
