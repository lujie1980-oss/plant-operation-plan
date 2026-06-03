package com.plantops.scenario.planning;

import com.plantops.api.dto.planning.SessionStepPatchDto;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DetailScheduleSessionMutationTest {

    @Test
    void moveOperationToAnotherLine() {
        DetailSchedule schedule = twoLineSchedule();
        OperationAssignment op = schedule.getOperations().get(0);

        DetailScheduleSessionMutation.applyPatches(
                schedule,
                List.of(new SessionStepPatchDto(op.getOperationId(), "L2", 1, null)));

        assertEquals("OP-1", schedule.getLines().get(1).getAssignedOperations().get(0).getOperationId());
        assertEquals(1, schedule.getLines().get(1).getAssignedOperations().size());
        assertEquals(0, schedule.getLines().get(0).getAssignedOperations().size());
    }

    @Test
    void unassignClearsLineShadow() {
        DetailSchedule schedule = twoLineSchedule();
        OperationAssignment op = schedule.getOperations().get(0);
        op.setLine(schedule.getLines().get(0));
        op.setStartMinute(100);

        DetailScheduleSessionMutation.applyPatches(
                schedule,
                List.of(new SessionStepPatchDto(op.getOperationId(), "", null, null)));

        assertEquals(0, schedule.getLines().get(0).getAssignedOperations().size());
        assertNull(op.getLine());
        assertNull(op.getStartMinute());
    }

    @Test
    void reorderOnSameLine() {
        DetailSchedule schedule = lineWithTwoOps();
        OperationAssignment second = schedule.getOperations().get(1);

        DetailScheduleSessionMutation.applyPatches(
                schedule,
                List.of(new SessionStepPatchDto(second.getOperationId(), null, 1, null)));

        assertEquals(
                second.getOperationId(),
                schedule.getLines().get(0).getAssignedOperations().get(0).getOperationId());
    }

    private static DetailSchedule twoLineSchedule() {
        ScheduleLine l1 = new ScheduleLine("L1", "R1", "A", true, 24 * 60);
        ScheduleLine l2 = new ScheduleLine("L2", "R2", "A", true, 24 * 60);
        OperationAssignment op = new OperationAssignment();
        op.setOperationId("OP-1");
        op.setWorkOrderNo("WO-1");
        op.setDurationMinutes(60);
        op.setEarliestStartMinute(0);
        l1.getAssignedOperations().add(op);

        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(List.of(l1, l2));
        schedule.setOperations(List.of(op));
        return schedule;
    }

    private static DetailSchedule lineWithTwoOps() {
        ScheduleLine line = new ScheduleLine("L1", "R1", "A", true, 24 * 60);
        OperationAssignment a = new OperationAssignment();
        a.setOperationId("OP-A");
        a.setWorkOrderNo("WO-1");
        a.setDurationMinutes(30);
        a.setEarliestStartMinute(0);
        OperationAssignment b = new OperationAssignment();
        b.setOperationId("OP-B");
        b.setWorkOrderNo("WO-1");
        b.setDurationMinutes(30);
        b.setEarliestStartMinute(0);
        line.getAssignedOperations().add(a);
        line.getAssignedOperations().add(b);

        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(List.of(line));
        schedule.setOperations(List.of(a, b));
        return schedule;
    }
}
