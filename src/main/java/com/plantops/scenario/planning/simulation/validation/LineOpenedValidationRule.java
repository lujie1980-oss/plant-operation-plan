package com.plantops.scenario.planning.simulation.validation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LineOpenedValidationRule implements ValidationRule {

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
        if (op.getLine() != null && !op.getLine().isOpened()) {
            violations.add(ValidationSupport.violation(
                    ScheduleConstraintViolation.ViolationLevel.HARD,
                    "LINE_NOT_OPENED",
                    op,
                    "产线未开线: " + op.getLine().getLineId()));
        }
        return violations;
    }
}
