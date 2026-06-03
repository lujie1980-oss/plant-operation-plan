package com.plantops.scenario.planning.simulation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.solver.detailschedule.OperationAssignment;

import java.util.List;

public interface ValidationRule {

    String ruleTypeId();

    boolean enabled(SimulationRuleContext ctx);

    List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op);
}
