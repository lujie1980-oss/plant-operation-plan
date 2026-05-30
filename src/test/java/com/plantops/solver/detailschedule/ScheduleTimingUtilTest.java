package com.plantops.solver.detailschedule;

import com.plantops.scenario.ChangeoverRuleIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTimingUtilTest {

    @Test
    void pairedOperationsShareStartAndEndOnSameLine() {
        ScheduleLine line = new ScheduleLine("L1", "YD-13", "A1", true, 480);
        OperationAssignment first = pairedOp("OP-1", "A", line, 1, 60);
        OperationAssignment second = pairedOp("OP-2", "B", line, 2, 60);
        first.setPairMateOperationId(second.getOperationId());
        second.setPairMateOperationId(first.getOperationId());

        DetailSchedule schedule = new DetailSchedule();
        schedule.setOperations(List.of(first, second));
        schedule.setShiftCapacityMinutes(480);
        schedule.setProblemFacts(new DetailScheduleProblemFacts(
                ScheduleContractSettings.defaults(),
                java.time.LocalDate.now(),
                480,
                new ChangeoverRuleIndex(List.of())));

        ScheduleTimingUtil.applyLineStartTimes(schedule);

        assertEquals(first.getStartMinute(), second.getStartMinute());
        assertEquals(first.getEndMinute(), second.getEndMinute());
        assertEquals(60, first.getDurationMinutes());
    }

    private static OperationAssignment pairedOp(
            String id, String product, ScheduleLine line, int sequenceHint, int duration) {
        OperationAssignment op = new OperationAssignment();
        op.setOperationId(id);
        op.setProductCode(product);
        op.setResourceId(line.getResourceId());
        op.setOperationName("成品");
        op.setDurationMinutes(duration);
        op.setSequenceHint(sequenceHint);
        op.setParallelPaired(true);
        op.setPairGroupId("PAIR-1");
        op.setLine(line);
        return op;
    }
}
