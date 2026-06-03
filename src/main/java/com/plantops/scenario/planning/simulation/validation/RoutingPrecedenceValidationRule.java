package com.plantops.scenario.planning.simulation.validation;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.scenario.planning.simulation.timing.RoutingChainTimingRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class RoutingPrecedenceValidationRule implements ValidationRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.OPERATION_TRANSFER_TIME;
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
        if (op.getRoutingPredecessor() != null && op.getStartMinute() != null) {
            int violationMinutes = RoutingChainTimingRule.routingPrecedenceViolationMinutes(
                    op, RoutingChainTimingRule.transferRules(ctx));
            if (violationMinutes > 0) {
                violations.add(ValidationSupport.violation(
                        ScheduleConstraintViolation.ViolationLevel.HARD,
                        "ROUTING_PRECEDENCE",
                        op,
                        "违反工艺链衔接，需推迟 " + violationMinutes + " 分钟"));
            }
        }
        return violations;
    }
}
