package com.plantops.scenario.planning.simulation;

import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;

import java.util.OptionalInt;

/** 产线队列扫描中的赋时规则插件。 */
public interface TimingRule {

    /** 与 {@link com.plantops.masterdata.BusinessRuleTypeIds} 对齐；无独立项时返回 null。 */
    String ruleTypeId();

    int order();

    boolean enabled(SimulationRuleContext ctx);

    /** 同线上一道 → 下一道之间的分钟增量（如换型）。 */
    default int gapBeforeNext(
            SimulationRuleContext ctx,
            OperationAssignment previous,
            OperationAssignment next,
            ScheduleLine line) {
        return 0;
    }

    /** 对单工序 earliest 下界的额外贡献（契约、工艺链等）。 */
    default int earliestFloorMinute(SimulationRuleContext ctx, OperationAssignment op) {
        return 0;
    }

    default void afterLineQueuePass(SimulationRuleContext ctx, ScheduleLine line) {
    }

    /** 冻结工序：返回固定开工分钟（相对锚点），跳过后续 cursor 计算。 */
    default OptionalInt fixedStartMinute(SimulationRuleContext ctx, OperationAssignment op) {
        return OptionalInt.empty();
    }

    /** 将 tentative 开工分钟对齐到规则窗口（如班次日历）。 */
    default int snapStartMinute(
            SimulationRuleContext ctx,
            OperationAssignment op,
            ScheduleLine line,
            int tentativeStart) {
        return tentativeStart;
    }
}
