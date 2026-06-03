package com.plantops.scenario.planning.simulation.validation;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class ParallelPairValidationRule implements ValidationRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.PARALLEL_OPERATIONS;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op) {
        return check(ctx, op, new HashSet<>());
    }

    public List<ScheduleConstraintViolation> check(
            SimulationRuleContext ctx,
            OperationAssignment op,
            Set<String> seenParallelGroups) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op.getLine() == null || !op.isParallelPaired() || op.getPairGroupId() == null) {
            return violations;
        }
        String groupKey = op.getPairGroupId();
        if (!seenParallelGroups.add(groupKey)) {
            return violations;
        }
        checkParallelPair(ctx, op, violations);
        return violations;
    }

    private static void checkParallelPair(
            SimulationRuleContext ctx,
            OperationAssignment seed,
            List<ScheduleConstraintViolation> violations) {
        String groupId = seed.getPairGroupId();
        List<OperationAssignment> group = ctx.schedule().getOperations().stream()
                .filter(o -> groupId.equals(o.getPairGroupId()) && o.isParallelPaired())
                .toList();
        if (group.size() < 2) {
            violations.add(ValidationSupport.violation(
                    ScheduleConstraintViolation.ViolationLevel.HARD,
                    "PARALLEL_PAIR_INCOMPLETE",
                    seed,
                    "并行工序对不完整"));
            return;
        }
        OperationAssignment first = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            OperationAssignment other = group.get(i);
            if (first.getLine() != null
                    && other.getLine() != null
                    && !Objects.equals(first.getLine().getLineId(), other.getLine().getLineId())) {
                violations.add(ValidationSupport.violation(
                        ScheduleConstraintViolation.ViolationLevel.HARD,
                        "PARALLEL_SAME_LINE",
                        other,
                        "并行工序须在同一产线"));
            }
            if (first.getStartMinute() != null
                    && other.getStartMinute() != null
                    && (!first.getStartMinute().equals(other.getStartMinute())
                            || !Objects.equals(first.getEndMinute(), other.getEndMinute()))) {
                violations.add(ValidationSupport.violation(
                        ScheduleConstraintViolation.ViolationLevel.HARD,
                        "PARALLEL_SAME_TIME",
                        other,
                        "并行工序须同起同止"));
            }
        }
    }
}
