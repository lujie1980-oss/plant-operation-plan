package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.OperationTransferTimeIndex;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LineChainTimingUtilTest {

    @Test
    void pairedOperationsShareStartOnSameLineQueue() {
        ScheduleLine line = new ScheduleLine("L1", "YD-13", "A1", true, 480);
        OperationAssignment first = pairedOp("OP-1", "A", 1, 60);
        OperationAssignment second = pairedOp("OP-2", "B", 2, 60);
        first.setPairMateOperationId(second.getOperationId());
        second.setPairMateOperationId(first.getOperationId());
        line.getAssignedOperations().addAll(List.of(first, second));

        DetailSchedule schedule = scheduleWith(line, first, second);

        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertEquals(first.getStartMinute(), second.getStartMinute());
        assertEquals(60, first.getDurationMinutes());
    }

    @Test
    void unKittedOperationStartsNoEarlierThanKittingLock() {
        ScheduleLine line = new ScheduleLine("L1", "YD-13", "A1", true, 480);
        OperationAssignment op = op("OP-1", "A", "成品", 0, 60);
        op.setKittingEligible(false);
        op.setEarliestStartMinute(24 * 60);
        line.getAssignedOperations().add(op);

        DetailSchedule schedule = scheduleWith(line, op);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertEquals(24 * 60, op.getStartMinute());
    }

    @Test
    void changeoverInsertsGapBetweenDifferentProductsOnSameLine() {
        ScheduleLine line = new ScheduleLine("L1", "YD-13", "A1", true, 480);
        OperationAssignment first = op("OP-1", "P-A", "工序1", 0, 60);
        OperationAssignment second = op("OP-2", "P-B", "工序1", 1, 60);
        line.getAssignedOperations().addAll(List.of(first, second));
        ChangeoverRuleIndex rules = new ChangeoverRuleIndex(List.of(
                new ChangeoverRuleIndex.Rule("工序1", "productCode", "P-A", "P-B", 25)));

        DetailSchedule schedule = scheduleWith(line, first, second, rules, null);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertEquals(0, first.getStartMinute());
        assertEquals(85, second.getStartMinute());
    }

    @Test
    void routingMinTransferDelaysSuccessorOnDifferentLines() {
        ScheduleLine lineA = new ScheduleLine("L1", "R-A", "A1", true, 480);
        ScheduleLine lineB = new ScheduleLine("L2", "R-B", "A1", true, 480);
        OperationAssignment op1 = op("OP-WO-1", "P-001", "裁线", 0, 60);
        op1.setWorkOrderNo("WO-1");
        op1.setOperationSeq(1);
        OperationAssignment op2 = op("OP-WO-2", "P-001", "半成品", 1, 60);
        op2.setWorkOrderNo("WO-1");
        op2.setOperationSeq(2);
        op2.setRoutingPredecessor(op1);
        lineA.getAssignedOperations().add(op1);
        lineB.getAssignedOperations().add(op2);

        OperationTransferTimeIndex transferRules = new OperationTransferTimeIndex(List.of(
                OperationTransferTimeIndex.standardRule("P-001", "裁线", "半成品", 30, 15)));

        DetailSchedule schedule = scheduleWith(List.of(lineA, lineB), List.of(op1, op2), null, transferRules);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertEquals(0, op1.getStartMinute());
        assertEquals(75, op2.getStartMinute());
    }

    @Test
    void bumpEarliestFromRoutingChain_updatesSuccessorEarliestStart() {
        OperationAssignment pred = new OperationAssignment();
        pred.setWorkOrderNo("WO-1");
        pred.setProductCode("P-001");
        pred.setOperationName("裁线");
        pred.setOperationSeq(1);
        pred.setStartMinute(100);
        pred.setDurationMinutes(60);
        OperationAssignment succ = new OperationAssignment();
        succ.setWorkOrderNo("WO-1");
        succ.setProductCode("P-001");
        succ.setOperationName("半成品");
        succ.setOperationSeq(2);
        succ.setEarliestStartMinute(0);
        succ.setRoutingPredecessor(pred);

        DetailSchedule schedule = new DetailSchedule();
        schedule.setOperations(List.of(pred, succ));
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                ScheduleContractSettings.defaults(),
                LocalDate.now(),
                new ChangeoverRuleIndex(List.of()),
                new OperationTransferTimeIndex(List.of(
                        OperationTransferTimeIndex.standardRule("P-001", "裁线", "半成品", 30, 15)))));

        assertTrue(LineChainTimingUtil.bumpEarliestFromRoutingChain(
                schedule,
                schedule.getProblemFacts().transferRules(),
                java.util.Map.of()));
        assertEquals(175, succ.getEarliestStartMinute());
    }

    @Test
    void applyAllStartTimes_enforcesRoutingOrderWhenIntermediateStepUnassigned() {
        ScheduleLine cutLine = new ScheduleLine("L-CUT", "R-CUT", "A1", true, 480);
        ScheduleLine labelLine = new ScheduleLine("L-LBL", "R-LBL", "A1", true, 480);
        OperationAssignment cut = op("OP-BAT-1-1_0", "P-001", "裁线", 0, 60);
        cut.setWorkOrderNo("WO-1");
        cut.setOperationSeq(1);
        OperationAssignment semi = op("OP-BAT-1-2_0", "P-001", "半成品", 1, 45);
        semi.setWorkOrderNo("WO-1");
        semi.setOperationSeq(2);
        semi.setRoutingPredecessor(cut);
        OperationAssignment label = op("OP-BAT-1-3_0", "P-001", "标签", 2, 30);
        label.setWorkOrderNo("WO-1");
        label.setOperationSeq(3);
        label.setRoutingPredecessor(semi);

        cutLine.getAssignedOperations().add(cut);
        labelLine.getAssignedOperations().add(label);
        cut.setStartMinute(120);
        label.setStartMinute(0);

        DetailSchedule schedule = scheduleWith(List.of(cutLine, labelLine), List.of(cut, semi, label), null, null);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertTrue(cut.getEndMinute() <= label.getStartMinute());
    }

    @Test
    void minimumStartRespectingRoutingChain_passesThroughUnassignedIntermediate() {
        OperationAssignment cut = op("OP-1", "P-001", "裁线", 0, 60);
        cut.setStartMinute(100);
        OperationAssignment semi = op("OP-2", "P-001", "半成品", 1, 40);
        semi.setRoutingPredecessor(cut);
        OperationAssignment label = op("OP-3", "P-001", "标签", 2, 20);
        label.setRoutingPredecessor(semi);

        OperationTransferTimeIndex transferRules = new OperationTransferTimeIndex(List.of());
        Integer required = LineChainTimingUtil.minimumStartRespectingRoutingChain(label, transferRules);

        assertEquals(200, required);
    }

    @Test
    void applyAllStartTimes_enforcesRoutingOrderWhenSuccessorWasScheduledTooEarly() {
        ScheduleLine cutLine = new ScheduleLine("L-CUT", "R-CUT", "A1", true, 480);
        ScheduleLine semiLine = new ScheduleLine("L-SEMI", "R-SEMI", "A1", true, 480);
        OperationAssignment cut = op("OP-WO-1-1_0", "P-001", "裁线", 0, 60);
        cut.setWorkOrderNo("WO-1");
        cut.setOperationSeq(1);
        OperationAssignment semi = op("OP-WO-1-2_0", "P-001", "半成品", 1, 60);
        semi.setWorkOrderNo("WO-1");
        semi.setOperationSeq(2);
        semi.setRoutingPredecessor(cut);
        // 错误初始态：半成品早于裁线（跨产线）
        cutLine.getAssignedOperations().add(cut);
        semiLine.getAssignedOperations().add(semi);
        cut.setStartMinute(120);
        semi.setStartMinute(0);

        DetailSchedule schedule = scheduleWith(List.of(cutLine, semiLine), List.of(cut, semi), null, null);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertTrue(cut.getStartMinute() <= semi.getStartMinute());
        assertTrue(cut.getEndMinute() <= semi.getStartMinute());
    }

    @Test
    void clampAndRequeue_fixesLabelScheduledBeforeCutAcrossLines() {
        ScheduleLine cutLine = new ScheduleLine("L-CUT", "R-CUT", "A1", true, 480);
        ScheduleLine labelLine = new ScheduleLine("L-LBL", "R-LBL", "A1", true, 480);
        OperationAssignment cut = op("OP-BAT-1-1_0", "P-001", "裁线", 0, 60);
        cut.setWorkOrderNo("WO-1");
        cut.setOperationSeq(1);
        OperationAssignment semi = op("OP-BAT-1-2_0", "P-001", "半成品", 1, 40);
        semi.setWorkOrderNo("WO-1");
        semi.setOperationSeq(2);
        semi.setRoutingPredecessor(cut);
        OperationAssignment label = op("OP-BAT-1-3_0", "P-001", "标签", 2, 20);
        label.setWorkOrderNo("WO-1");
        label.setOperationSeq(3);
        label.setRoutingPredecessor(semi);
        cutLine.getAssignedOperations().add(cut);
        labelLine.getAssignedOperations().add(label);
        cut.setStartMinute(500);
        label.setStartMinute(100);

        DetailSchedule schedule = scheduleWith(List.of(cutLine, labelLine), List.of(cut, semi, label), null, null);
        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertTrue(label.getStartMinute() >= cut.getEndMinute());
    }

    @Test
    void applyAllStartTimes_skipsContractStartWhenWaitDisabled() {
        ScheduleLine line = new ScheduleLine("L1", "R-A", "A1", true, 480);
        OperationAssignment op = op("OP-1", "P-001", "裁线", 0, 60);
        op.setMpContractStartDate(LocalDate.now().plusDays(10));
        line.getAssignedOperations().add(op);

        ScheduleContractSettings waitOff = new ScheduleContractSettings(
                100, 20, 60,
                ScheduleContractPenaltyMode.LINEAR,
                ScheduleContractPenaltyMode.QUADRATIC,
                0,
                false,
                false);
        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(List.of(line));
        schedule.setOperations(List.of(op));
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                waitOff,
                LocalDate.now(),
                new ChangeoverRuleIndex(List.of()),
                new OperationTransferTimeIndex(List.of())));

        LineChainTimingUtil.applyAllStartTimes(schedule);

        assertEquals(0, op.getStartMinute());
    }

    private static DetailSchedule scheduleWith(ScheduleLine line, OperationAssignment... ops) {
        return scheduleWith(List.of(line), List.of(ops), null, null);
    }

    private static DetailSchedule scheduleWith(
            ScheduleLine line,
            OperationAssignment op1,
            OperationAssignment op2,
            ChangeoverRuleIndex changeover,
            OperationTransferTimeIndex transfer) {
        return scheduleWith(List.of(line), List.of(op1, op2), changeover, transfer);
    }

    private static DetailSchedule scheduleWith(
            List<ScheduleLine> lines,
            List<OperationAssignment> ops,
            ChangeoverRuleIndex changeover,
            OperationTransferTimeIndex transfer) {
        DetailSchedule schedule = new DetailSchedule();
        schedule.setLines(lines);
        schedule.setOperations(ops);
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                ScheduleContractSettings.defaults(),
                LocalDate.now(),
                changeover != null ? changeover : new ChangeoverRuleIndex(List.of()),
                transfer != null ? transfer : new OperationTransferTimeIndex(List.of())));
        return schedule;
    }

    private static OperationAssignment op(
            String id, String product, String operationName, int sequenceHint, int duration) {
        OperationAssignment op = new OperationAssignment();
        op.setOperationId(id);
        op.setProductCode(product);
        op.setResourceId("R1");
        op.setOperationName(operationName);
        op.setDurationMinutes(duration);
        op.setSequenceHint(sequenceHint);
        return op;
    }

    private static OperationAssignment pairedOp(String id, String product, int sequenceHint, int duration) {
        OperationAssignment op = op(id, product, "成品", sequenceHint, duration);
        op.setParallelPaired(true);
        op.setPairGroupId("PAIR-1");
        return op;
    }
}
