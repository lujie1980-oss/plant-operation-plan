package com.plantops.scenario.planning.simulation.validation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EarliestStartValidationRule implements ValidationRule {

    @Override
    public String ruleTypeId() {
        return null;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return true;
    }

    @Override
    public List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op.getLine() == null) {
            return violations;
        }
        if (op.getStartMinute() != null && op.getStartMinute() < op.getEarliestStartMinute()) {
            violations.add(ValidationSupport.violation(
                    ScheduleConstraintViolation.ViolationLevel.MEDIUM,
                    "EARLIEST_START_VIOLATION",
                    op,
                    "开工早于最早可排时间（齐套/契约）"));
        }
        return violations;
    }
}
