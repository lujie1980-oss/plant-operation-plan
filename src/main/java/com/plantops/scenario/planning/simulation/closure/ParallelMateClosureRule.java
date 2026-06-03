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
public class ParallelMateClosureRule implements AffectedClosureRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.PARALLEL_OPERATIONS;
    }

    @Override
    public String profileRuleKey() {
        return "parallel-mate";
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isClosureRuleEnabled(ctx, this);
    }

    @Override
    public void expand(
            SimulationRuleContext ctx,
            Map<String, OperationAssignment> byId,
            OperationAssignment current,
            Set<String> affected,
            ArrayDeque<String> pending) {
        if (current == null || current.getPairMateOperationId() == null) {
            return;
        }
        if (affected.add(current.getPairMateOperationId())) {
            pending.add(current.getPairMateOperationId());
        }
    }
}
