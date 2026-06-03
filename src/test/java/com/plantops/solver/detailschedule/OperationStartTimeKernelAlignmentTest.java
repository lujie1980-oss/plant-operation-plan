package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;
import com.plantops.scenario.planning.simulation.DetailScheduleTimingKernel;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 4：全局 kernel 赋时后，按产线链式关系用 shadow 计算器应得到相同 startMinute。
 */
@QuarkusTest
class OperationStartTimeKernelAlignmentTest {

    @Inject
    DetailScheduleTimingKernel timingKernel;

    @Test
    void perOperationCalculatorMatchesKernelAfterAssign() {
        ScheduleLine line = new ScheduleLine("L1", "YD-13", "A1", true, 480);
        OperationAssignment first = op("OP-1", "P-A", "工序1", 0, 60);
        OperationAssignment second = op("OP-2", "P-B", "工序1", 1, 60);
        line.getAssignedOperations().addAll(List.of(first, second));
        ChangeoverRuleIndex rules = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("工序1", "productCode", "P-A", "P-B", 25)));

        DetailSchedule schedule = scheduleWith(line, first, second, rules, null);
        LineChainTimingUtil.applyAllStartTimes(schedule);
        wirePreviousOnLine(line);

        assertEquals(first.getStartMinute(), timingKernel.computeShadowStartMinute(first, schedule));
        assertEquals(second.getStartMinute(), timingKernel.computeShadowStartMinute(second, schedule));
        assertEquals(first.getStartMinute(), OperationStartTimeCalculator.compute(first, schedule));
        assertEquals(second.getStartMinute(), OperationStartTimeCalculator.compute(second, schedule));
    }

    @Test
    void crossLineRoutingMatchesKernel() {
        ScheduleLine lineA = new ScheduleLine("L1", "R-A", "A1", true, 480);
        ScheduleLine lineB = new ScheduleLine("L2", "R-B", "A1", true, 480);
        OperationAssignment op1 = op("OP-1", "P-001", "裁线", 0, 60);
        OperationAssignment op2 = op("OP-2", "P-001", "半成品", 1, 60);
        op2.setRoutingPredecessor(op1);
        lineA.getAssignedOperations().add(op1);
        lineB.getAssignedOperations().add(op2);

        OperationTransferTimeIndex transferRules = new OperationTransferTimeIndex(List.of(
                OperationTransferTimeIndex.standardRule("P-001", "裁线", "半成品", 30, 15)));
        DetailSchedule schedule = scheduleWith(List.of(lineA, lineB), List.of(op1, op2), null, transferRules);
        LineChainTimingUtil.applyAllStartTimes(schedule);
        wirePreviousOnLine(lineA);
        wirePreviousOnLine(lineB);

        assertEquals(0, op1.getStartMinute());
        assertEquals(75, op2.getStartMinute());
        assertEquals(op1.getStartMinute(), timingKernel.computeShadowStartMinute(op1, schedule));
        assertEquals(op2.getStartMinute(), timingKernel.computeShadowStartMinute(op2, schedule));
        assertEquals(op1.getStartMinute(), OperationStartTimeCalculator.compute(op1, schedule));
        assertEquals(op2.getStartMinute(), OperationStartTimeCalculator.compute(op2, schedule));
    }

    private static void wirePreviousOnLine(ScheduleLine line) {
        List<OperationAssignment> queue = line.getAssignedOperations();
        for (int i = 0; i < queue.size(); i++) {
            OperationAssignment op = queue.get(i);
            if (i > 0) {
                op.setPreviousOnLine(queue.get(i - 1));
            }
        }
    }

    private static OperationAssignment op(
            String id, String product, String opName, int seq, int duration) {
        OperationAssignment op = new OperationAssignment();
        op.setOperationId(id);
        op.setProductCode(product);
        op.setOperationName(opName);
        op.setOperationSeq(seq);
        op.setDurationMinutes(duration);
        op.setEarliestStartMinute(0);
        op.setResourceId("R1");
        return op;
    }

    private static DetailSchedule scheduleWith(
            ScheduleLine line,
            OperationAssignment first,
            OperationAssignment second,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules) {
        return scheduleWith(List.of(line), List.of(first, second), changeoverRules, transferRules);
    }

    private static DetailSchedule scheduleWith(
            List<ScheduleLine> lines,
            List<OperationAssignment> operations,
            ChangeoverRuleIndex changeoverRules,
            OperationTransferTimeIndex transferRules) {
        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(lines);
        schedule.setOperations(operations);
        for (OperationAssignment op : operations) {
            for (ScheduleLine line : lines) {
                if (line.getAssignedOperations().contains(op)) {
                    op.setLine(line);
                }
            }
        }
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                ScheduleContractSettings.defaults(),
                LocalDate.now(),
                changeoverRules != null ? changeoverRules : ChangeoverRuleIndex.fromWorkspace(),
                transferRules != null ? transferRules : new OperationTransferTimeIndex(List.of())));
        return schedule;
    }
}
