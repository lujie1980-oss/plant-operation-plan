package com.plantops.scenario.planning.simulation;

import com.plantops.scenario.planning.ScheduleConstraintViolation;
import com.plantops.solver.detailschedule.DetailSchedule;
import com.plantops.solver.detailschedule.OperationAssignment;
import com.plantops.solver.detailschedule.ScheduleLine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class SimulationPipeline {

    public record SimulationResult(
            SimulationMode mode,
            long durationMs,
            List<String> recalculatedOperationIds,
            List<ScheduleConstraintViolation> violations) {
    }

    @Inject
    DetailScheduleTimingKernel timingKernel;

    @Inject
    ValidationPipeline validationPipeline;

    @Inject
    SimulationClosureExpander closureExpander;

    public SimulationResult fullSimulate(DetailSchedule schedule) {
        long start = System.currentTimeMillis();
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.FULL, Set.of());
        timingKernel.applyAllStartTimes(ctx, schedule);
        List<String> recalculated = scheduledOperationIds(schedule);
        List<ScheduleConstraintViolation> violations = validationPipeline.validate(ctx);
        return new SimulationResult(
                SimulationMode.FULL,
                System.currentTimeMillis() - start,
                recalculated,
                violations);
    }

    public SimulationResult incrementalSimulate(
            DetailSchedule schedule,
            Collection<String> seedOperationIds) {
        long start = System.currentTimeMillis();
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.INCREMENTAL, Set.copyOf(seedOperationIds));
        Set<String> affected = closureExpander.expand(ctx, seedOperationIds);
        timingKernel.applyAllStartTimes(ctx, schedule);
        List<String> recalculated = affected.isEmpty()
                ? scheduledOperationIds(schedule)
                : new ArrayList<>(affected);
        List<ScheduleConstraintViolation> violations = validationPipeline.validate(ctx);
        return new SimulationResult(
                SimulationMode.INCREMENTAL,
                System.currentTimeMillis() - start,
                recalculated,
                violations);
    }

    private static List<String> scheduledOperationIds(DetailSchedule schedule) {
        List<String> ids = new ArrayList<>();
        if (schedule.getOperations() == null) {
            return ids;
        }
        for (OperationAssignment op : schedule.getOperations()) {
            if (op.getStartMinute() != null
                    && op.getOperationId() != null
                    && isAssignedToLine(schedule, op)) {
                ids.add(op.getOperationId());
            }
        }
        return ids;
    }

    private static boolean isAssignedToLine(DetailSchedule schedule, OperationAssignment op) {
        if (op.getLine() != null) {
            return true;
        }
        if (schedule.getLines() == null) {
            return false;
        }
        for (ScheduleLine line : schedule.getLines()) {
            if (line.getAssignedOperations() != null && line.getAssignedOperations().contains(op)) {
                return true;
            }
        }
        return false;
    }
}
