package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.PeriodSequenceSpec;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationTimeAnchor;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationTimeWindowDerivationTest {

    private static final LocalDate PLANNING_START = LocalDate.of(2026, 6, 1);

    private static OntologyGraph graphWithOperations(LocalDate needDate) {
        SupplyOrder supplyOrder = new SupplyOrder(
                "SO-1", "P1", OntologyIds.pispId("P1"), 10, needDate,
                SupplyOrderStatus.OPEN, SupplyOrderType.PLANNED_PRODUCTION);
        long[] elapsedMinutes = {1440, 2880, 1440};
        OntologyGraph.Builder builder = OntologyGraph.builder()
                .supplyOrder(supplyOrder)
                .periodsOrdered(PeriodSequenceSpec.defaultSpec().expand(PLANNING_START));
        for (int i = 0; i < elapsedMinutes.length; i++) {
            Operation operation = new Operation(
                    OntologyIds.operationId("SO-1", i), "SO-1", i, "OP-" + i);
            operation.setProductionDuration(elapsedMinutes[i] * 60);
            builder.operation(operation);
        }
        return builder.build();
    }

    @Test
    void timeWindowsFollowJitChain() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 20));
        OperationTimeWindowDerivations.recalculate(g, "SO-1", PLANNING_START);
        List<Operation> ops = g.operationsForSupplyOrder("SO-1");
        LocalDateTime horizon = OperationTimeAnchor.horizonStart(PLANNING_START);

        assertEquals(horizon, ops.get(0).getEarliestPossibleStartOwn());
        assertEquals(horizon, ops.get(1).getEarliestPossibleStartOwn());
        assertEquals(horizon.plusDays(1), ops.get(0).getEarliestPossibleEndOwn());
        assertEquals(horizon.plusDays(2), ops.get(1).getEarliestPossibleEndOwn());

        assertEquals(horizon, ops.get(0).getEarliestPossibleStartTotal());
        assertEquals(horizon.plusDays(1), ops.get(1).getEarliestPossibleStartTotal());
        assertEquals(horizon.plusDays(3), ops.get(2).getEarliestPossibleStartTotal());

        assertEquals(LocalDate.of(2026, 6, 20), ops.get(2).getLatestDesiredEnd().toLocalDate());
        assertEquals(LocalDate.of(2026, 6, 19), ops.get(1).getLatestDesiredEnd().toLocalDate());
        assertEquals(LocalDate.of(2026, 6, 17), ops.get(0).getLatestDesiredEnd().toLocalDate());

        assertNull(ops.get(0).getPlannedStartTotal());
        assertNull(ops.get(0).getPlannedEndTotal());
        assertFalse(ops.get(0).isInfeasible());
        assertFalse(ops.get(1).isInfeasible());
        assertFalse(ops.get(2).isInfeasible());
    }

    @Test
    void flagsInfeasibleWhenWindowEmpty() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 2));
        OperationTimeWindowDerivations.recalculate(g, "SO-1", PLANNING_START);
        List<Operation> ops = g.operationsForSupplyOrder("SO-1");
        assertTrue(ops.stream().allMatch(Operation::isInfeasible));
    }

    @Test
    void needDateChangePropagatesViaEngine() {
        OntologyGraph g = graphWithOperations(LocalDate.of(2026, 6, 20));
        RolEngine engine = RolEngine.withMasterPlanRules(g);
        engine.applySupplyOrderNeedDateChange(g.supplyOrder("SO-1"), LocalDate.of(2026, 6, 25));
        assertEquals(LocalDate.of(2026, 6, 25),
                g.operationsForSupplyOrder("SO-1").get(2).getLatestDesiredEnd().toLocalDate());
        assertEquals(LocalDate.of(2026, 6, 22),
                g.operationsForSupplyOrder("SO-1").get(0).getLatestDesiredEnd().toLocalDate());
    }
}
