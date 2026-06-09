package com.plantops.scenario.planning.simulation;

import com.plantops.masterdata.BusinessRuleScopeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

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

    public OptionalInt fixedStartMinute(SimulationRuleContext ctx, com.plantops.solver.detailschedule.OperationAssignment op) {
        for (TimingRule rule : enabledTimingRules(ctx)) {
            OptionalInt fixed = rule.fixedStartMinute(ctx, op);
            if (fixed.isPresent()) {
                return fixed;
            }
        }
        return OptionalInt.empty();
    }

    public int snapStartMinute(
            SimulationRuleContext ctx,
            com.plantops.solver.detailschedule.OperationAssignment op,
            com.plantops.solver.detailschedule.ScheduleLine line,
            int tentativeStart) {
        int snapped = tentativeStart;
        for (TimingRule rule : enabledTimingRules(ctx)) {
            snapped = rule.snapStartMinute(ctx, op, line, snapped);
        }
        return snapped;
    }

    public boolean isRuleTypeEnabled(SimulationRuleContext ctx, String ruleTypeId) {
        if (ruleTypeId == null || ruleTypeId.isBlank()) {
            return true;
        }
        // 预加载的启用规则项快照（请求线程注入）视为权威：求解线程据此判断，避免在
        // 无 CDI 请求上下文/事务的 SolverManager 工作线程上访问 JPA。未预加载时回退实时查询。
        Set<String> enabled = ctx.enabledRuleTypes();
        if (enabled != null) {
            if (!enabled.contains(ruleTypeId)) {
                return false;
            }
        } else if (!ruleScope.isDetailScheduleEnabled(ruleTypeId)) {
            return false;
        }
        return ctx.profileSettings().isRuleEnabled(ruleTypeId, true);
    }

    public boolean isClosureRuleEnabled(SimulationRuleContext ctx, AffectedClosureRule rule) {
        if (!ctx.profileSettings().isRuleEnabled(rule.profileRuleKey(), true)) {
            return false;
        }
        String ruleTypeId = rule.ruleTypeId();
        if (ruleTypeId == null || ruleTypeId.isBlank()) {
            return true;
        }
        return isRuleTypeEnabled(ctx, ruleTypeId);
    }

    public List<String> collectAppliedRuleIds(SimulationRuleContext ctx, boolean includeClosureRules) {
        Set<String> ids = new LinkedHashSet<>();
        for (TimingRule rule : enabledTimingRules(ctx)) {
            String key = rule.ruleTypeId();
            if (key != null && !key.isBlank()) {
                ids.add(key);
            }
        }
        for (ValidationRule rule : enabledValidationRules(ctx)) {
            String key = rule.ruleTypeId();
            if (key != null && !key.isBlank()) {
                ids.add(key);
            } else {
                ids.add(rule.getClass().getSimpleName());
            }
        }
        if (includeClosureRules) {
            for (AffectedClosureRule rule : enabledClosureRules(ctx)) {
                ids.add(rule.profileRuleKey());
            }
        }
        return new ArrayList<>(ids);
    }
}
