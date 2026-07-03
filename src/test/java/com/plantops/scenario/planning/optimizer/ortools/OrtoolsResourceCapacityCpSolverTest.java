package com.plantops.scenario.planning.optimizer.ortools;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.solver.masterplan.BomDependencyEdge;
import com.plantops.solver.masterplan.MasterPlanCapacityOverlay;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.MasterPlanSettings;
import com.plantops.solver.masterplan.OperationPrecedenceFact;
import com.plantops.solver.masterplan.ResourceCapacityAssignment;
import com.plantops.solver.masterplan.TimeSlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OrtoolsResourceCapacityCpSolverTest {

    private static boolean nativeAvailableFlag;

    @BeforeAll
    static void loadNative() {
        try {
            OrtoolsResourceCapacityCpSolver.ensureNativeLoaded();
            nativeAvailableFlag = true;
        } catch (UnsatisfiedLinkError | Exception ex) {
            nativeAvailableFlag = false;
        }
    }

    static boolean nativeAvailable() {
        return nativeAvailableFlag;
    }

    @Test
    @EnabledIf("nativeAvailable")
    void splitsOperationAcrossTwoResources() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = new ArrayList<>();
        int index = 0;
        for (String resourceId : List.of("RES-A", "RES-B")) {
            for (int d = 0; d < 5; d++) {
                slots.add(new TimeSlot(
                        resourceId + "-D" + d,
                        index++,
                        start.plusDays(d),
                        "DAY",
                        resourceId,
                        480));
            }
        }

        String workOrderNo = "WO-SPLIT-1";
        int totalMinutes = 600;
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(workOrderNo, 1, "RES-A", 0, totalMinutes, 480, slots.subList(0, 5)),
                assignment(workOrderNo, 1, "RES-B", 0, totalMinutes, 480, slots.subList(5, 10)));

        MasterPlanSchedule schedule = schedule(slots, assignments);
        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible(), outcome.scoreSummary());
        int sum = outcome.assigned().stream().mapToInt(ResourceCapacityAssignment::getAssignedMinutes).sum();
        assertEquals(totalMinutes, sum);
    }

    @Test
    @EnabledIf("nativeAvailable")
    void singleResourceUsesFullLoad() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480),
                new TimeSlot("RES-A-D1", 1, start.plusDays(1), "DAY", "RES-A", 480));
        String workOrderNo = "WO-SINGLE-1";
        int totalMinutes = 300;
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(workOrderNo, 1, "RES-A", 0, totalMinutes, 480, slots));

        MasterPlanSchedule schedule = schedule(slots, assignments);
        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible());
        assertEquals(totalMinutes, outcome.assigned().get(0).getAssignedMinutes());
    }

    @Test
    @EnabledIf("nativeAvailable")
    void serialOperationsOnSameDayAreFeasible() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480),
                new TimeSlot("RES-A-D1", 1, start.plusDays(1), "DAY", "RES-A", 480));
        String workOrderNo = "WO-SERIAL-1";
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(workOrderNo, 1, "RES-A", 0, 200, 480, slots),
                assignment(workOrderNo, 2, "RES-A", 0, 150, 480, slots));

        MasterPlanSchedule schedule = schedule(slots, assignments);
        schedule.setOperationPrecedenceFacts(List.of(
                new OperationPrecedenceFact(workOrderNo, 1, 2)));

        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible(), outcome.scoreSummary());
        assertEquals(200, outcome.assigned().stream()
                .filter(a -> a.getOperationSeq() == 1)
                .mapToInt(ResourceCapacityAssignment::getAssignedMinutes)
                .sum());
        assertEquals(150, outcome.assigned().stream()
                .filter(a -> a.getOperationSeq() == 2)
                .mapToInt(ResourceCapacityAssignment::getAssignedMinutes)
                .sum());
    }

    @Test
    @EnabledIf("nativeAvailable")
    void splitAcrossResourcesWithUnusedDaySegmentRows() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = new ArrayList<>();
        int index = 0;
        for (String resourceId : List.of("RES-A", "RES-B")) {
            for (int d = 0; d < 5; d++) {
                slots.add(new TimeSlot(
                        resourceId + "-D" + d,
                        index++,
                        start.plusDays(d),
                        "DAY",
                        resourceId,
                        480));
            }
        }
        String workOrderNo = "WO-SEG-UNUSED";
        int totalMinutes = 600;
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(workOrderNo, 1, "RES-A", 0, totalMinutes, 480, slots.subList(0, 5)),
                assignment(workOrderNo, 1, "RES-A", 1, totalMinutes, 480, slots.subList(0, 5)),
                assignment(workOrderNo, 1, "RES-B", 0, totalMinutes, 480, slots.subList(5, 10)),
                assignment(workOrderNo, 1, "RES-B", 1, totalMinutes, 480, slots.subList(5, 10)));

        MasterPlanSchedule schedule = schedule(slots, assignments);
        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible(), outcome.scoreSummary());
        assertEquals(totalMinutes, outcome.assigned().stream()
                .mapToInt(ResourceCapacityAssignment::getAssignedMinutes)
                .sum());
    }

    @Test
    @EnabledIf("nativeAvailable")
    void relaxesBomWhenFullModelInfeasible() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> parentSlots = List.of(
                new TimeSlot("RES-P-D0", 0, start, "DAY", "RES-P", 480));
        List<TimeSlot> childSlots = List.of(
                new TimeSlot("RES-C-D1", 1, start.plusDays(1), "DAY", "RES-C", 480));
        String parentWo = "WO-PARENT";
        String childWo = "WO-CHILD";
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(parentWo, 1, "RES-P", 0, 100, 480, parentSlots),
                assignment(childWo, 1, "RES-C", 0, 100, 480, childSlots));

        MasterPlanSchedule schedule = schedule(
                List.of(parentSlots.get(0), childSlots.get(0)),
                assignments);
        schedule.setBomDependencyEdges(List.of(
                new BomDependencyEdge(parentWo, childWo)));

        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible(), outcome.scoreSummary());
        assertTrue(outcome.scoreSummary().contains("relaxed"),
                "expected BOM relaxation note: " + outcome.scoreSummary());
    }

    @Test
    @EnabledIf("nativeAvailable")
    void allowsSlotOverloadWithFiniteCapacityStrategy() {
        assumeTrue(nativeAvailableFlag);
        LocalDate start = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("RES-A-D0", 0, start, "DAY", "RES-A", 480));
        String workOrderNo = "WO-OVERLOAD-1";
        int totalMinutes = 600;
        List<ResourceCapacityAssignment> assignments = List.of(
                assignment(workOrderNo, 1, "RES-A", 0, totalMinutes, 480, slots));

        MasterPlanSchedule schedule = schedule(slots, assignments);
        schedule.getPlanningSettings().setCapacityStrategy(
                com.plantops.solver.masterplan.MasterPlanCapacityStrategy.FINITE_CAPACITY);

        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, null);

        assertTrue(outcome.feasible(), outcome.scoreSummary());
        assertEquals(totalMinutes, outcome.assigned().stream()
                .mapToInt(ResourceCapacityAssignment::getAssignedMinutes)
                .sum());
        assertTrue(outcome.capacityOverloadMinutes() > 0, "expected overload soft penalty");
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
        schedule.setOperationPrecedenceFacts(List.of());
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
        a.setProductCode("FG-TEST");
        a.setOperationTotalMinutes(operationTotalMinutes);
        a.setSlotCapacityMinutes(slotCapacityMinutes);
        a.setWorkOrderQuantity(BigDecimal.ONE);
        a.setEligibleTimeSlots(eligible);
        return a;
    }
}
