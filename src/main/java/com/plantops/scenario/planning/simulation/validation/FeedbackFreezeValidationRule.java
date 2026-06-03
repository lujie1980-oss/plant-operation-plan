package com.plantops.scenario.planning.simulation.validation;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.FeedbackFreezeIndex;
import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FeedbackFreezeValidationRule implements ValidationRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.FEEDBACK_FREEZE;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId())
                && freezeIndex(ctx).cutoff() != null;
    }

    @Override
    public List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op == null || op.getOperationId() == null || op.getStartMinute() == null) {
            return violations;
        }
        var index = freezeIndex(ctx);
        if (index == null) {
            return violations;
        }
        Integer frozen = index.frozenStartMinute(op.getOperationId());
        if (frozen == null) {
            return violations;
        }
        if (!frozen.equals(op.getStartMinute())) {
            violations.add(ValidationSupport.violation(
                    ScheduleConstraintViolation.ViolationLevel.MEDIUM,
                    "FEEDBACK_FROZEN_START_MOVED",
                    op,
                    "冻结工序开工时间应为 " + frozen + " 分，当前 " + op.getStartMinute() + " 分"));
        }
        return violations;
    }

    private static FeedbackFreezeIndex freezeIndex(SimulationRuleContext ctx) {
        return ctx.facts() != null ? ctx.facts().feedbackFreeze() : FeedbackFreezeIndex.empty();
    }
}
