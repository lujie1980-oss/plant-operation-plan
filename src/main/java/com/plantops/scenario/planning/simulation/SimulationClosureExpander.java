package com.plantops.scenario.planning.simulation;

import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SimulationClosureExpander {

    @Inject
    SimulationRuleRegistry registry;

    public Set<String> expand(SimulationRuleContext ctx, Collection<String> seedOperationIds) {
        Set<String> affected = new LinkedHashSet<>();
        if (seedOperationIds == null || seedOperationIds.isEmpty()) {
            return affected;
        }
        DetailSchedule schedule = ctx.schedule();
        Map<String, OperationAssignment> byId = indexById(schedule);
        ArrayDeque<String> pending = new ArrayDeque<>();
        for (String seed : seedOperationIds) {
            if (seed != null && !seed.isBlank() && byId.containsKey(seed) && affected.add(seed)) {
                pending.add(seed);
            }
        }

        while (!pending.isEmpty()) {
            String id = pending.poll();
            OperationAssignment current = byId.get(id);
            if (current == null) {
                continue;
            }
            for (AffectedClosureRule rule : registry.enabledClosureRules(ctx)) {
                rule.expand(ctx, byId, current, affected, pending);
            }
        }
        return affected;
    }

    private static Map<String, OperationAssignment> indexById(DetailSchedule schedule) {
        Map<String, OperationAssignment> map = new java.util.HashMap<>();
        if (schedule.getOperations() != null) {
            for (OperationAssignment op : schedule.getOperations()) {
                if (op.getOperationId() != null) {
                    map.put(op.getOperationId(), op);
                }
            }
        }
        return map;
    }
}
