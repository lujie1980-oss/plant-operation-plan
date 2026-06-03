package com.plantops.scenario.planning;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DetailScheduleSimulationEngineTest {

    @Inject
    DetailScheduleSimulationEngine simulationEngine;

    @Test
    void fullSimulateAssignsStartTimes() {
        DetailSchedule schedule = sampleSchedule();
        DetailScheduleSimulationEngine.SimulationResult result = simulationEngine.fullSimulate(schedule);
        assertNotNull(result);
        OperationAssignment op = schedule.getOperations().get(0);
        assertNotNull(op.getStartMinute());
        assertFalse(result.recalculatedOperationIds().isEmpty());
    }

    @Test
    void incrementalExpandsRoutingSuccessor() {
        DetailSchedule schedule = chainedSchedule();
        var affected = DetailScheduleSimulationEngine.expandAffectedClosure(
                schedule, List.of("OP-A"));
        assertTrue(affected.contains("OP-A"));
        assertTrue(affected.contains("OP-B"));
    }

    private static DetailSchedule sampleSchedule() {
        ScheduleLine line = new ScheduleLine("L1", "R1", "A", true, 24 * 60);
        OperationAssignment op = new OperationAssignment();
        op.setOperationId("OP-1");
        op.setWorkOrderNo("WO-1");
        op.setDurationMinutes(60);
        op.setEarliestStartMinute(0);
        op.setKittingEligible(true);
        line.getAssignedOperations().add(op);

        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(List.of(line));
        schedule.setOperations(List.of(op));
        return schedule;
    }

    private static DetailSchedule chainedSchedule() {
        ScheduleLine line1 = new ScheduleLine("L1", "R1", "A", true, 24 * 60);
        ScheduleLine line2 = new ScheduleLine("L2", "R2", "A", true, 24 * 60);

        OperationAssignment a = new OperationAssignment();
        a.setOperationId("OP-A");
        a.setWorkOrderNo("WO-1");
        a.setDurationMinutes(30);
        a.setEarliestStartMinute(0);
        a.setProductCode("P1");
        a.setOperationName("Cut");
        line1.getAssignedOperations().add(a);

        OperationAssignment b = new OperationAssignment();
        b.setOperationId("OP-B");
        b.setWorkOrderNo("WO-1");
        b.setDurationMinutes(30);
        b.setEarliestStartMinute(0);
        b.setProductCode("P1");
        b.setOperationName("Label");
        b.setRoutingPredecessor(a);
        line2.getAssignedOperations().add(b);

        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(List.of(line1, line2));
        schedule.setOperations(List.of(a, b));
        return schedule;
    }
}
