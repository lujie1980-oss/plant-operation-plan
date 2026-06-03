package com.plantops.scenario.planning.simulation.validation;

import com.plantops.masterdata.BusinessRuleTypeIds;
import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.scenario.planning.simulation.SimulationRuleContext;
import com.plantops.scenario.planning.simulation.SimulationRuleRegistry;
import com.plantops.scenario.planning.simulation.ValidationRule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class BatchContinuousValidationRule implements ValidationRule {

    @Inject
    SimulationRuleRegistry registry;

    @Override
    public String ruleTypeId() {
        return BusinessRuleTypeIds.BATCH_CONTINUOUS;
    }

    @Override
    public boolean enabled(SimulationRuleContext ctx) {
        return registry.isRuleTypeEnabled(ctx, ruleTypeId());
    }

    @Override
    public List<ScheduleConstraintViolation> check(SimulationRuleContext ctx, OperationAssignment op) {
        List<ScheduleConstraintViolation> violations = new ArrayList<>();
        if (op == null || op.getBatchNo() == null || op.getBatchNo().isBlank() || op.getLine() == null) {
            return violations;
        }
        ScheduleLine line = op.getLine();
        List<OperationAssignment> queue = line.getAssignedOperations();
        if (queue == null || queue.size() < 2) {
            return violations;
        }
        Map<String, List<Integer>> batchIndices = new HashMap<>();
        for (int i = 0; i < queue.size(); i++) {
            OperationAssignment item = queue.get(i);
            if (item.getBatchNo() == null || item.getBatchNo().isBlank()) {
                continue;
            }
            batchIndices.computeIfAbsent(item.getBatchNo(), k -> new ArrayList<>()).add(i);
        }
        List<Integer> indices = batchIndices.get(op.getBatchNo());
        if (indices == null || indices.size() < 2) {
            return violations;
        }
        int min = indices.stream().min(Integer::compareTo).orElse(0);
        int max = indices.stream().max(Integer::compareTo).orElse(min);
        for (int i = min + 1; i < max; i++) {
            OperationAssignment between = queue.get(i);
            if (between.getBatchNo() == null || !op.getBatchNo().equals(between.getBatchNo())) {
                violations.add(ValidationSupport.violation(
                        ScheduleConstraintViolation.ViolationLevel.MEDIUM,
                        "BATCH_INTERLEAVED",
                        op,
                        "批次 " + op.getBatchNo() + " 在同线队列中被其它批次/工序隔开"));
                break;
            }
        }
        return violations;
    }
}
