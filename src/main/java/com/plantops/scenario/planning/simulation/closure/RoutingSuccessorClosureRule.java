package com.plantops.scenario.planning.simulation.closure;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.planning.simulation.AffectedClosureRule;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class RoutingSuccessorClosureRule implements AffectedClosureRule {

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
    public void expand(
            SimulationRuleContext ctx,
            Map<String, OperationAssignment> byId,
            OperationAssignment current,
            Set<String> affected,
            ArrayDeque<String> pending) {
        if (ctx.schedule().getOperations() == null || current == null) {
            return;
        }
        for (OperationAssignment op : ctx.schedule().getOperations()) {
            if (op.getRoutingPredecessor() == current
                    && op.getOperationId() != null
                    && affected.add(op.getOperationId())) {
                pending.add(op.getOperationId());
            }
        }
    }
}
