package com.plantops.scenario.planning.simulation;

import com.plantops.solver.detailschedule.OperationAssignment;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

public interface AffectedClosureRule {

    String ruleTypeId();

    boolean enabled(SimulationRuleContext ctx);

    void expand(
            SimulationRuleContext ctx,
            Map<String, OperationAssignment> byId,
            OperationAssignment current,
            Set<String> affected,
            ArrayDeque<String> pending);
}
