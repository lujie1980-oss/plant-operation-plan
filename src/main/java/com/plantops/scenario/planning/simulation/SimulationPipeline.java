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
            List<ScheduleConstraintViolation> violations,
            List<String> appliedRules,
            String simulationProfileId) {
    }

    @Inject
    DetailScheduleTimingKernel timingKernel;

    @Inject
    ValidationPipeline validationPipeline;

    @Inject
    SimulationClosureExpander closureExpander;

    @Inject
    SimulationRuleRegistry registry;

    public SimulationResult fullSimulate(DetailSchedule schedule) {
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.FULL, Set.of());
        return simulate(schedule, ctx, true, List.of());
    }

    public SimulationResult incrementalSimulate(DetailSchedule schedule, Collection<String> seedOperationIds) {
        SimulationRuleContext ctx = SimulationRuleContextFactory.from(
                schedule, SimulationMode.INCREMENTAL, Set.copyOf(seedOperationIds));
        return simulate(schedule, ctx, false, seedOperationIds);
    }

    public SimulationResult simulate(
            DetailSchedule schedule,
            SimulationRuleContext ctx,
            boolean fullReschedule,
            Collection<String> seedOperationIds) {
        long start = System.currentTimeMillis();
        SimulationMode mode;
        List<String> recalculated;
        boolean includeClosure = false;

        if (fullReschedule || seedOperationIds == null || seedOperationIds.isEmpty()) {
            mode = SimulationMode.FULL;
            timingKernel.applyAllStartTimes(ctx, schedule);
            recalculated = scheduledOperationIds(schedule);
        } else {
            mode = SimulationMode.INCREMENTAL;
            includeClosure = true;
            Set<String> affected = closureExpander.expand(ctx, seedOperationIds);
            timingKernel.applyAllStartTimes(ctx, schedule);
            recalculated = affected.isEmpty()
                    ? scheduledOperationIds(schedule)
                    : new ArrayList<>(affected);
        }

        List<ScheduleConstraintViolation> violations = validationPipeline.validate(ctx);
        List<String> appliedRules = registry.collectAppliedRuleIds(ctx, includeClosure);
        String profileId = ctx.profileSettings().profileId();

        return new SimulationResult(
                mode,
                System.currentTimeMillis() - start,
                recalculated,
                violations,
                appliedRules,
                profileId);
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
