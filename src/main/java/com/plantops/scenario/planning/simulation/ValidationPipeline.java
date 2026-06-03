package com.plantops.scenario.planning.simulation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.plantops.scenario.planning.simulation.validation.ParallelPairValidationRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ValidationPipeline {

    @Inject
    SimulationRuleRegistry registry;

    public List<ScheduleConstraintViolation> validate(SimulationRuleContext ctx) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        DetailSchedule schedule = ctx.schedule();
        if (schedule == null || schedule.getOperations() == null) {
            return violations;
        }
        Set<String> seenParallelGroups = new HashSet<>();
        for (OperationAssignment op : schedule.getOperations()) {
            for (ValidationRule rule : registry.enabledValidationRules(ctx)) {
                if (rule instanceof ParallelPairValidationRule parallelRule) {
                    violations.addAll(parallelRule.check(ctx, op, seenParallelGroups));
                } else {
                    violations.addAll(rule.check(ctx, op));
                }
            }
        }
        return violations;
    }
}
