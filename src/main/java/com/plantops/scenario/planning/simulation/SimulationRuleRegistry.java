package com.plantops.scenario.planning.simulation;

import com.plantops.masterdata.BusinessRuleScopeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class SimulationRuleRegistry {

    @Inject
    Instance<TimingRule> timingRules;

    @Inject
    Instance<ValidationRule> validationRules;

    @Inject
    Instance<AffectedClosureRule> closureRules;

    @Inject
    BusinessRuleScopeService ruleScope;

    public List<TimingRule> enabledTimingRules(SimulationRuleContext ctx) {
        return timingRules.stream()
                .sorted(Comparator.comparingInt(TimingRule::order))
                .filter(rule -> rule.enabled(ctx))
                .toList();
    }

    public List<ValidationRule> enabledValidationRules(SimulationRuleContext ctx) {
        return validationRules.stream()
                .filter(rule -> rule.enabled(ctx))
                .toList();
    }

    public List<AffectedClosureRule> enabledClosureRules(SimulationRuleContext ctx) {
        return closureRules.stream()
                .filter(rule -> rule.enabled(ctx))
                .toList();
    }

    public int sumGapBeforeNext(
            SimulationRuleContext ctx,
            com.plantops.solver.detailschedule.OperationAssignment previous,
            com.plantops.solver.detailschedule.OperationAssignment next,
            com.plantops.solver.detailschedule.ScheduleLine line) {
        int total = 0;
        for (TimingRule rule : enabledTimingRules(ctx)) {
            total += rule.gapBeforeNext(ctx, previous, next, line);
        }
        return total;
    }

    public int maxEarliestFloorMinute(SimulationRuleContext ctx, com.plantops.solver.detailschedule.OperationAssignment op) {
        int floor = 0;
        for (TimingRule rule : enabledTimingRules(ctx)) {
            floor = Math.max(floor, rule.earliestFloorMinute(ctx, op));
        }
        return floor;
    }

    public boolean isRuleTypeEnabled(SimulationRuleContext ctx, String ruleTypeId) {
        if (ruleTypeId == null || ruleTypeId.isBlank()) {
            return true;
        }
        if (ctx.enabledRuleTypes() != null && !ctx.enabledRuleTypes().contains(ruleTypeId)) {
            return false;
        }
        return ruleScope.isDetailScheduleEnabled(ruleTypeId);
    }
}
