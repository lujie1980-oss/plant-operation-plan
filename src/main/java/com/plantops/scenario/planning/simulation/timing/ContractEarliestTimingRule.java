package com.plantops.scenario.planning.simulation.timing;

import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.TimingRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleContractSettings;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ContractEarliestTimingRule implements TimingRule {

    @Override
    public String ruleTypeId() {
        return null;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return true;
    }

    @Override
    public int earliestFloorMinute(SimulationRuleContext ctx, OperationAssignment op) {
        ScheduleContractSettings contractSettings = ctx.facts() != null
                ? ctx.facts().contractSettings()
                : ScheduleContractSettings.defaults();
        return contractSettings.contractStartMinuteFloor(op, ctx.planningAnchorDate());
    }

    public static int effectiveEarliestStartMinute(
            OperationAssignment op,
            SimulationRuleContext ctx) {
        return Math.max(
                op != null ? op.getEarliestStartMinute() : 0,
                contractFloor(op, ctx));
    }

    public static int effectiveEarliestStartMinute(
            OperationAssignment left,
            OperationAssignment right,
            SimulationRuleContext ctx) {
        return Math.max(
                effectiveEarliestStartMinute(left, ctx),
                effectiveEarliestStartMinute(right, ctx));
    }

    private static int contractFloor(OperationAssignment op, SimulationRuleContext ctx) {
        ScheduleContractSettings contractSettings = ctx.facts() != null
                ? ctx.facts().contractSettings()
                : ScheduleContractSettings.defaults();
        return contractSettings.contractStartMinuteFloor(op, ctx.planningAnchorDate());
    }
}
