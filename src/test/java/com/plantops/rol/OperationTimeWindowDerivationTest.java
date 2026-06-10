package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTimeWindowDerivationTest {

    private static OntologyGraph graphWithOperations(LocalDate needDate) {
        SupplyOrder supplyOrder = new SupplyOrder(
                "SO-1", "P1", OntologyIds.pispId("P1"), 10, needDate,
                SupplyOrderStatus.OPEN, SupplyOrderType.PLANNED_PRODUCTION);
        double[] productionTimes = {1440, 2880, 1440};
        OntologyGraph.Builder builder = OntologyGraph.builder()
                .supplyOrder(supplyOrder)
                .periodsOrdered(PeriodSequenceSpec.defaultSpec().expand(LocalDate.of(2026, 6, 1)));
        for (int i = 0; i < productionTimes.length; i++) {
            builder.operation(new Operation(
                    OntologyIds.operationId("SO-1", i), "SO-1", i, "OP-" + i, productionTimes[i]));
        }
        return builder.build();
    }

    @Test
    void timeWindowsFollowJitChain() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 20));
        OperationTimeWindowDerivations.recalculate(g, "SO-1", LocalDate.of(2026, 6, 1));
        List<Operation> ops = g.operationsForSupplyOrder("SO-1");
        assertEquals(LocalDate.of(2026, 6, 20), ops.get(2).getLatestPossibleEnd());
        assertEquals(LocalDate.of(2026, 6, 19), ops.get(1).getLatestPossibleEnd()); // 20 − 1d (op2 prod)
        assertEquals(LocalDate.of(2026, 6, 17), ops.get(0).getLatestPossibleEnd()); // 19 − 2d (op1 prod)
        assertEquals(LocalDate.of(2026, 6, 1), ops.get(0).getEarliestPossibleStart());
        assertEquals(LocalDate.of(2026, 6, 2), ops.get(1).getEarliestPossibleStart()); // 1 + 1d (op0 prod)
        assertEquals(LocalDate.of(2026, 6, 4), ops.get(2).getEarliestPossibleStart()); // 2 + 2d (op1 prod)
        assertFalse(ops.get(0).isInfeasible());
        assertFalse(ops.get(1).isInfeasible());
        assertFalse(ops.get(2).isInfeasible());
    }

    @Test
    void flagsInfeasibleWhenWindowEmpty() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 2));
        OperationTimeWindowDerivations.recalculate(g, "SO-1", LocalDate.of(2026, 6, 1));
        List<Operation> ops = g.operationsForSupplyOrder("SO-1");
        assertTrue(ops.stream().anyMatch(Operation::isInfeasible));
    }

    @Test
    void needDateChangePropagatesViaEngine() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 20));
        RolEngine engine = RolEngine.withMasterPlanRules(g);
        engine.applySupplyOrderNeedDateChange(g.supplyOrder("SO-1"), LocalDate.of(2026, 6, 25));
        assertEquals(LocalDate.of(2026, 6, 25),
                g.operationsForSupplyOrder("SO-1").get(2).getLatestPossibleEnd());
    }
}
