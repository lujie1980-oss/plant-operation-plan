package com.plantops.scenario.planning.simulation.closure;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.planning.simulation.AffectedClosureRule;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class BatchContinuousClosureRule implements AffectedClosureRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.BATCH_CONTINUOUS;
    }

    @Override
    public String profileRuleKey() {
        return "batch-continuous";
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
        if (current == null || current.getBatchNo() == null || current.getBatchNo().isBlank()) {
            return;
        }
        ScheduleLine line = current.getLine();
        if (line == null || line.getAssignedOperations() == null) {
            return;
        }
        String batchNo = current.getBatchNo();
        for (OperationAssignment mate : line.getAssignedOperations()) {
            if (mate.getOperationId() == null || mate.getBatchNo() == null) {
                continue;
            }
            if (!batchNo.equals(mate.getBatchNo())) {
                continue;
            }
            if (affected.add(mate.getOperationId())) {
                pending.add(mate.getOperationId());
            }
        }
        DetailSchedule schedule = ctx.schedule();
        if (schedule == null || schedule.getOperations() == null) {
            return;
        }
        for (OperationAssignment op : schedule.getOperations()) {
            if (op.getOperationId() == null || op.getBatchNo() == null) {
                continue;
            }
            if (!batchNo.equals(op.getBatchNo())) {
                continue;
            }
            if (line.getLineId() != null
                    && op.getLine() != null
                    && line.getLineId().equals(op.getLine().getLineId())
                    && affected.add(op.getOperationId())) {
                pending.add(op.getOperationId());
            }
        }
    }
}
