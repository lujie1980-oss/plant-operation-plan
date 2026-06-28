package com.plantops.scenario.planning;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JitResourceCapacitySeederTest {

    @Test
    void parentFirstBomOrderVisitsParentBeforeChild() {
        List<BomDependencyEdge> edges = List.of(
                new BomDependencyEdge("WO-PARENT", "WO-CHILD"));
        List<String> order = JitResourceCapacitySeeder.parentFirstBomOrder(
                edges, Set.of("WO-PARENT", "WO-CHILD"));
        assertEquals(List.of("WO-PARENT", "WO-CHILD"), order);
    }

    @Test
    void seedWorkOrderBackwardAssignsMinutesAcrossResources() {
        LocalDate start = LocalDate.of(2026, 6, 10);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480),
                new TimeSlot("RES-B-D0", 1, start, "DAY", "RES-B", 480));

        ResourceCapacityAssignment resA = assignment("WO-1", 1, "RES-A", 0, 600, 480, 1, slots.subList(0, 1));
        ResourceCapacityAssignment resB = assignment("WO-1", 1, "RES-B", 0, 600, 480, 2, slots.subList(1, 2));
        List<ResourceCapacityAssignment> woAssignments = List.of(resA, resB);

        LocalDateTime endAnchor = LocalDateTime.of(2026, 6, 10, 17, 0);
        JitResourceCapacitySeeder.seedWorkOrderBackward(woAssignments, endAnchor, new HashMap<>());

        int total = resA.getAssignedMinutes() + resB.getAssignedMinutes();
        assertEquals(600, total);
        assertTrue(resA.getAssignedMinutes() > 0 || resB.getAssignedMinutes() > 0);
    }

    @Test
    void seedUsesOperationLatestDesiredEndPerOperation() {
        LocalDate start = LocalDate.of(2026, 6, 10);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480),
                new TimeSlot("RES-A-D1", 1, start.plusDays(1), "DAY", "RES-A", 480));

        ResourceCapacityAssignment op1 = assignment("WO-1", 1, "RES-A", 0, 240, 480, 1, slots);
        op1.setOperationLatestDesiredEnd(LocalDateTime.of(2026, 6, 10, 17, 0));
        ResourceCapacityAssignment op2 = assignment("WO-1", 2, "RES-A", 0, 120, 480, 1, slots);
        op2.setOperationLatestDesiredEnd(LocalDateTime.of(2026, 6, 10, 12, 0));

        List<ResourceCapacityAssignment> woAssignments = List.of(op1, op2);
        JitResourceCapacitySeeder.seedWorkOrderBackward(
                woAssignments,
                LocalDateTime.of(2026, 6, 11, 17, 0),
                new HashMap<>());

        assertEquals(240, op1.getAssignedMinutes());
        assertEquals(120, op2.getAssignedMinutes());
        assertTrue(op2.getTimeSlot() != null);
        assertTrue(op1.getTimeSlot() != null);
    }

    @Test
    void validatorAcceptsConservedFeasibleSeed() {
        LocalDate start = LocalDate.of(2026, 6, 10);
        TimeSlot slotA = new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480);
        TimeSlot slotB = new TimeSlot("RES-B-D0", 1, start, "DAY", "RES-B", 480);
        ResourceCapacityAssignment a = assignment("WO-1", 1, "RES-A", 0, 300, 480, 1, List.of(slotA));
        a.setTimeSlot(slotA);
        a.setAssignedMinutes(180);
        ResourceCapacityAssignment b = assignment("WO-1", 1, "RES-B", 0, 300, 480, 2, List.of(slotB));
        b.setTimeSlot(slotB);
        b.setAssignedMinutes(120);

        MasterPlanSchedule schedule = new MasterPlanSchedule(
                List.of(slotA, slotB),
                List.of(),
                start,
                new MasterPlanSettings(),
                null,
                null,
                List.of(),
                MasterPlanCapacityOverlay.empty(),
                List.of(),
                List.of(),
                null,
                new ChangeoverRuleIndex(List.of()));
        schedule.setOperationPrecedenceFacts(List.of());

        assertTrue(ResourceCapacitySeedValidator.isFeasible(List.of(a, b), schedule));
    }

    private static ResourceCapacityAssignment assignment(
            String workOrderNo,
            int operationSeq,
            String resourceId,
            int daySeg,
            int operationTotalMinutes,
            int slotCapacityMinutes,
            int resourcePriority,
            List<TimeSlot> eligible) {
        ResourceCapacityAssignment a = new ResourceCapacityAssignment();
        a.setId(ResourceCapacityAssignment.allocationId(workOrderNo, operationSeq, resourceId, daySeg));
        a.setWorkOrderNo(workOrderNo);
        a.setOperationSeq(operationSeq);
        a.setOperationKey(ResourceCapacityAssignment.operationKey(workOrderNo, operationSeq));
        a.setDaySegmentIndex(daySeg);
        a.setResourceId(resourceId);
        a.setResourcePriority(resourcePriority);
        a.setOperationTotalMinutes(operationTotalMinutes);
        a.setSlotCapacityMinutes(slotCapacityMinutes);
        a.setWorkOrderQuantity(BigDecimal.ONE);
        a.setEligibleTimeSlots(eligible);
        return a;
    }
}
