package com.plantops.scenario.planning.simulation.closure;

import com.plantops.scenario.planning.simulation.SimulationMode;
import com.plantops.scenario.planning.simulation.SimulationProfileSettings;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchContinuousClosureRuleTest {

    @Test
    void expandsAllBatchMatesOnSameLine() {
        ScheduleLine line = new ScheduleLine("L1", "R1", "A1", true, 480);
        OperationAssignment seed = op("OP-1", "B-001", 1, line);
        OperationAssignment mate = op("OP-2", "B-001", 2, line);
        OperationAssignment other = op("OP-3", "B-002", 3, line);
        line.setAssignedOperations(List.of(seed, other, mate));

        Map<String, OperationAssignment> byId = Map.of(
                seed.getOperationId(), seed,
                mate.getOperationId(), mate,
                other.getOperationId(), other);

        SimulationRuleContext ctx = new SimulationRuleContext(
                new DetailSchedule(),
                null,
                null,
                Map.of(),
                SimulationMode.INCREMENTAL,
                Set.of(seed.getOperationId()),
                LocalDate.now(),
                new SimulationProfileSettings("SP-TEST", 16, Map.of("batch-continuous", true), false));

        Set<String> affected = new HashSet<>(Set.of(seed.getOperationId()));
        ArrayDeque<String> pending = new ArrayDeque<>();
        BatchContinuousClosureRule rule = new BatchContinuousClosureRule();
        rule.expand(ctx, byId, seed, affected, pending);

        assertTrue(affected.contains("OP-1"));
        assertTrue(affected.contains("OP-2"));
    }

    private static OperationAssignment op(String id, String batchNo, int seq, ScheduleLine line) {
        OperationAssignment op = new OperationAssignment();
        op.setOperationId(id);
        op.setBatchNo(batchNo);
        op.setOperationSeq(seq);
        op.setDurationMinutes(30);
        op.setLine(line);
        return op;
    }
}
