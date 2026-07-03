package com.plantops.scenario.planning.optimizer.ortools;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedyResourceCapacitySolverTest {

    @Test
    void assignsOperationAcrossSlotsWhenCapacityTight() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 300),
                new TimeSlot("RES-A-D1", 1, start.plusDays(1), "DAY", "RES-A", 300));
        ResourceCapacityAssignment seg0 = assignment("WO-1", 1, "RES-A", 0, 500, 300, slots);
        ResourceCapacityAssignment seg1 = assignment("WO-1", 1, "RES-A", 1, 500, 300, slots);
        List<ResourceCapacityAssignment> assignments = List.of(seg0, seg1);
        MasterPlanSchedule schedule = schedule(slots, assignments);

        assertTrue(GreedyResourceCapacitySolver.tryAssign(assignments, assignments, schedule));
        assertTrue(seg0.getAssignedMinutes() + seg1.getAssignedMinutes() == 500);
    }

    private static MasterPlanSchedule schedule(List<TimeSlot> slots, List<ResourceCapacityAssignment> assignments) {
        MasterPlanSchedule schedule = new MasterPlanSchedule(
                slots,
                List.of(),
                LocalDate.of(2026, 6, 1),
                new MasterPlanSettings(),
                null,
                null,
                List.of(),
                MasterPlanCapacityOverlay.empty(),
                List.of(),
                List.of(),
                null,
                new ChangeoverRuleIndex(List.of()));
        schedule.setResourceCapacityAssignments(assignments);
        return schedule;
    }

    private static ResourceCapacityAssignment assignment(
            String workOrderNo,
            int operationSeq,
            String resourceId,
            int daySeg,
            int operationTotalMinutes,
            int slotCapacityMinutes,
            List<TimeSlot> eligible) {
        ResourceCapacityAssignment a = new ResourceCapacityAssignment();
        a.setId(ResourceCapacityAssignment.allocationId(workOrderNo, operationSeq, resourceId, daySeg));
        a.setWorkOrderNo(workOrderNo);
        a.setOperationSeq(operationSeq);
        a.setResourcePriority(1);
        a.setOperationKey(ResourceCapacityAssignment.operationKey(workOrderNo, operationSeq));
        a.setDaySegmentIndex(daySeg);
        a.setResourceId(resourceId);
        a.setOperationTotalMinutes(operationTotalMinutes);
        a.setSlotCapacityMinutes(slotCapacityMinutes);
        a.setWorkOrderQuantity(BigDecimal.ONE);
        a.setEligibleTimeSlots(eligible);
        return a;
    }
}
