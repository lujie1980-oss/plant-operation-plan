package com.plantops.scenario.planning.simulation.timing;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.FeedbackFreezeIndex;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.TimingRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.OptionalInt;

@ApplicationScoped
public class FeedbackFreezeTimingRule implements TimingRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.FEEDBACK_FREEZE;
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId())
                && freezeIndex(ctx) != null
                && freezeIndex(ctx).cutoff() != null;
    }

    @Override
    public OptionalInt fixedStartMinute(SimulationRuleContext ctx, OperationAssignment op) {
        if (op == null || op.getOperationId() == null) {
            return OptionalInt.empty();
        }
        FeedbackFreezeIndex index = freezeIndex(ctx);
        if (index == null) {
            return OptionalInt.empty();
        }
        Integer frozen = index.frozenStartMinute(op.getOperationId());
        return frozen != null ? OptionalInt.of(frozen) : OptionalInt.empty();
    }

    static FeedbackFreezeIndex freezeIndex(SimulationRuleContext ctx) {
        return ctx.facts() != null ? ctx.facts().feedbackFreeze() : null;
    }
}
