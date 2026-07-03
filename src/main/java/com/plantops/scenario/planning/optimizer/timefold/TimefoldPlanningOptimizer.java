package com.plantops.scenario.planning.optimizer.timefold;

import com.plantops.scenario.planning.optimizer.OptimizerResult;
import com.plantops.scenario.planning.optimizer.OrderAllocationConverter;
import com.plantops.scenario.planning.optimizer.PlanningOptimizer;
import com.plantops.scenario.planning.optimizer.PlanningOptimizerException;
import com.plantops.scenario.planning.optimizer.PlanningProblem;
import com.plantops.scenario.planning.optimizer.PlanningProblemScheduleResolver;
import com.plantops.solver.masterplan.MasterPlanSchedule;
import com.plantops.solver.masterplan.OrderAllocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TimefoldPlanningOptimizer implements PlanningOptimizer {

    public static final String ENGINE_ID = "timefold";

    @Inject
    MasterPlanTimefoldSolver timefoldSolver;

    @Inject
    PlanningProblemScheduleResolver scheduleResolver;

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public OptimizerResult optimize(PlanningProblem problem) throws PlanningOptimizerException {
        if (problem == null) {
            throw new PlanningOptimizerException("PlanningProblem is required");
        }
        if (problem.context() == null && problem.ontologySchedule() == null) {
            throw new PlanningOptimizerException("PlanningProblem requires context or ontologySchedule");
        }
        try {
            long started = System.nanoTime();
            MasterPlanSchedule input = scheduleResolver.resolve(problem);
            MasterPlanSchedule solution = timefoldSolver.solve(input);
            long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            List<OrderAllocation> allocations = solution.getOrderAllocations().stream()
                    .filter(a -> a.getTimeSlot() != null)
                    .filter(a -> inScope(a, problem.scopedSupplyOrderIds()))
                    .toList();
            List<com.plantops.api.dto.MasterPlanAllocationDto> persistAllocations =
                    OrderAllocationConverter.toAllocationDtos(allocations);
            return new OptimizerResult(
                    ENGINE_ID,
                    OrderAllocationConverter.toPlanningAssignments(allocations),
                    solution.score() != null ? solution.score().toString() : null,
                    durationMs,
                    List.of(),
                    persistAllocations);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PlanningOptimizerException("Timefold solve interrupted", ex);
        } catch (Exception ex) {
            throw new PlanningOptimizerException("Timefold solve failed: " + ex.getMessage(), ex);
        }
    }

    private static boolean inScope(OrderAllocation allocation, Set<String> scopedSupplyOrderIds) {
        if (scopedSupplyOrderIds == null || scopedSupplyOrderIds.isEmpty()) {
            return true;
        }
        return scopedSupplyOrderIds.contains(allocation.getWorkOrderNo());
    }
}
