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
import java.util.List;

@ApplicationScoped
public class ContinuousProductionValidationRule implements ValidationRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.CONTINUOUS_PRODUCTION;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op.getLine() == null) {
            return violations;
        }
        violations.addAll(checkContinuousProduction(op));
        return violations;
    }

    private static List<ScheduleConstraintViolation> checkContinuousProduction(OperationAssignment op) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op.getContinuousGroupId() == null || op.getLine() == null || op.getStartMinute() == null) {
            return violations;
        }
        List<OperationAssignment> queue = op.getLine().getAssignedOperations();
        if (queue == null) {
            return violations;
        }
        int idx = queue.indexOf(op);
        if (idx < 0) {
            return violations;
        }
        for (int i = idx + 1; i < queue.size(); i++) {
            OperationAssignment other = queue.get(i);
            if (op.getContinuousGroupId().equals(other.getContinuousGroupId())) {
                continue;
            }
            violations.add(ValidationSupport.violation(
                    ScheduleConstraintViolation.ViolationLevel.HARD,
                    "CONTINUOUS_INTERLEAVED",
                    op,
                    "连续生产组被其它料号隔开"));
            break;
        }
        return violations;
    }
}
