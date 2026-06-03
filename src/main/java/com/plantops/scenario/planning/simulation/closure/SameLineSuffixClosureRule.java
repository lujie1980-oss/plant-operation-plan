package com.plantops.scenario.planning.simulation.closure;

import com.plantops.scenario.planning.simulation.AffectedClosureRule;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SameLineSuffixClosureRule implements AffectedClosureRule {

    @Override
    public String ruleTypeId() {
        return null;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return true;
    }

    @Override
    public void expand(
            SimulationRuleContext ctx,
            Map<String, OperationAssignment> byId,
            OperationAssignment current,
            Set<String> affected,
            ArrayDeque<String> pending) {
        ScheduleLine line = current != null ? current.getLine() : null;
        if (line == null || line.getAssignedOperations() == null) {
            return;
        }
        List<OperationAssignment> queue = line.getAssignedOperations();
        int startIdx = queue.indexOf(current);
        if (startIdx < 0) {
            return;
        }
        for (int i = startIdx; i < queue.size(); i++) {
            OperationAssignment suffix = queue.get(i);
            if (suffix.getOperationId() != null && affected.add(suffix.getOperationId())) {
                pending.add(suffix.getOperationId());
            }
        }
    }
}
