package com.plantops.scenario.planning.simulation.timing;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.ChangeoverRuleIndex;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.TimingRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChangeoverTimingRule implements TimingRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.CHANGEOVER;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public int gapBeforeNext(
            SimulationRuleContext ctx,
            OperationAssignment previous,
            OperationAssignment next,
            ScheduleLine line) {
        ChangeoverRuleIndex changeoverRules = ctx.facts() != null
                ? ctx.facts().changeoverRules()
                : ChangeoverRuleIndex.fromWorkspace();
        return changeoverGapMinutes(changeoverRules, previous, next);
    }

    public static int changeoverGapMinutes(
            ChangeoverRuleIndex changeoverRules,
            OperationAssignment previous,
            OperationAssignment next) {
        if (changeoverRules == null
                || previous == null
                || next == null
                || previous.getProductCode() == null
                || next.getProductCode() == null
                || next.getOperationName() == null
                || next.getOperationName().isBlank()) {
            return 0;
        }
        return Math.max(
                0,
                changeoverRules.computeMinutes(
                        next.getOperationName(),
                        next.getResourceId(),
                        next.getOperationSeq(),
                        previous.getProductCode(),
                        next.getProductCode()));
    }
}
