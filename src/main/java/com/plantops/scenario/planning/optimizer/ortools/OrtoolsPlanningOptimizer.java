package com.plantops.scenario.planning.optimizer.ortools;

import com.plantops.config.ParameterRegistry;
import com.plantops.scenario.planning.JitResourceCapacitySeeder;
import com.plantops.scenario.planning.ResourceCapacityResultProjector;
import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.OrderAllocationConverter;
import com.plantops.scenario.planning.optimizer.PlanningAssignment;
import com.plantops.scenario.planning.optimizer.PlanningOptimizer;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerException;
import com.plantops.scenario.planning.optimizer.PlanningProblem;
import com.plantops.scenario.planning.optimizer.PlanningProblemScheduleResolver;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class OrtoolsPlanningOptimizer implements PlanningOptimizer {

    public static final String ENGINE_ID = "ortools";

    @Inject
    PlanningProblemScheduleResolver scheduleResolver;

    @Inject
    ParameterRegistry parameters;

    @Inject
    JitResourceCapacitySeeder jitResourceCapacitySeeder;

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public OptimizerResult optimize(PlanningProblem problem) throws PlanningOptimizerException {
        if (problem == null) {
            throw new PlanningOptimizerException("PlanningProblem is required");
        }
        long started = System.nanoTime();
        try {
            MasterPlanSchedule schedule = scheduleResolver.resolve(problem);
            if (schedule.hasResourceCapacityAssignments()) {
                return optimizeResourceCapacity(schedule, problem, started);
            }
            if (parameters.getBoolean("master_plan_multi_resource_split", false)) {
                throw new PlanningOptimizerException(
                        "master_plan_multi_resource_split=true 需要 ResourceCapacityAssignment 问题体；"
                                + "请通过 Delivery Sandbox 本体图路径求解");
            }
            return optimizeOrderAllocations(schedule, problem, started);
        } catch (PlanningOptimizerException ex) {
            throw ex;
        } catch (UnsatisfiedLinkError ex) {
            throw new PlanningOptimizerException(
                    "OR-Tools native libraries unavailable on this platform: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new PlanningOptimizerException("OR-Tools solve failed: " + ex.getMessage(), ex);
        }
    }

    private OptimizerResult optimizeResourceCapacity(
            MasterPlanSchedule schedule,
            PlanningProblem problem,
            long started) throws PlanningOptimizerException {
        jitResourceCapacitySeeder.seedIfEnabled(schedule);
        OrtoolsResourceCapacityCpSolver.SolveOutcome outcome =
                OrtoolsResourceCapacityCpSolver.solve(schedule, problem.scopedSupplyOrderIds());
        if (!outcome.feasible()) {
            throw new PlanningOptimizerException("OR-Tools CP-SAT found no feasible multi-resource assignment");
        }
        var allocationDtos = ResourceCapacityResultProjector.toAllocationDtos(outcome.assigned());
        long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        List<PlanningAssignment> planningAssignments = allocationDtos.stream()
                .map(OrderAllocationConverter::toPlanningAssignment)
                .toList();
        return new OptimizerResult(
                ENGINE_ID,
                planningAssignments,
                outcome.scoreSummary(),
                durationMs,
                List.of(),
                allocationDtos);
    }

    private OptimizerResult optimizeOrderAllocations(
            MasterPlanSchedule schedule,
            PlanningProblem problem,
            long started) throws PlanningOptimizerException {
        OrtoolsMasterPlanCpSolver.SolveOutcome outcome =
                OrtoolsMasterPlanCpSolver.solve(schedule, problem.scopedSupplyOrderIds());
        if (!outcome.feasible()) {
            throw new PlanningOptimizerException("OR-Tools CP-SAT found no feasible assignment");
        }
        List<OrderAllocation> allocations = outcome.assignedAllocations().stream()
                .filter(a -> a.getTimeSlot() != null)
                .toList();
        long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        return new OptimizerResult(
                ENGINE_ID,
                OrderAllocationConverter.toPlanningAssignments(allocations),
                outcome.scoreSummary(),
                durationMs,
                List.of(),
                OrderAllocationConverter.toAllocationDtos(allocations));
    }
}
