package com.plantops.scenario.planning.optimizer;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.rol.ChangeSet;
import com.plantops.rol.RolEngine;
import com.plantops.rol.RolTransaction;
import com.plantops.ontology.supply.ResourceCapacityAssignmentProjection;
import com.plantops.scenario.planning.OperationPlannedTimeProjection;
import com.plantops.scenario.planning.OntologyTimefoldMapper;
import com.plantops.solver.masterplan.OrderAllocation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 将 {@link OptimizerResult} 回写到内存 {@link OntologyGraph}（Operation planned + ROL ChangeSet）。 */
@ApplicationScoped
public class PlanningResultApplicator {

    @Inject
    OntologyTimefoldMapper ontologyTimefoldMapper;

    @Inject
    RolTransaction rolTransaction;

    public OptimizerResult applyFromOptimizerResult(
            OntologyGraph graph,
            RolEngine rolEngine,
            OptimizerResult optimizerResult,
            Set<String> scopedSupplyOrderIds) {
        if (optimizerResult == null) {
            return null;
        }
        List<MasterPlanAllocationDto> allocationDtos =
                scopedAllocationDtos(optimizerResult.persistAllocations(), scopedSupplyOrderIds);
        applyAllocationDtos(graph, rolEngine, allocationDtos);
        return optimizerResult;
    }

    public OptimizerResult applyToGraph(
            OntologyGraph graph,
            RolEngine rolEngine,
            List<OrderAllocation> allocations,
            Set<String> scopedSupplyOrderIds,
            OptimizerResult optimizerResult) {
        if (graph == null || allocations == null || allocations.isEmpty()) {
            return optimizerResult;
        }
        List<MasterPlanAllocationDto> allocationDtos =
                OrderAllocationConverter.toAllocationDtos(scopedAllocations(allocations, scopedSupplyOrderIds));
        applyAllocationDtos(graph, rolEngine, allocationDtos);

        if (optimizerResult != null) {
            return optimizerResult;
        }
        return new OptimizerResult(
                null,
                OrderAllocationConverter.toPlanningAssignmentsFromDtos(allocationDtos),
                null,
                0L,
                List.of(),
                allocationDtos);
    }

    public OptimizerResult applyToGraph(
            OntologyGraph graph,
            RolEngine rolEngine,
            List<OrderAllocation> allocations,
            Set<String> scopedSupplyOrderIds,
            String engineId,
            String scoreSummary,
            long solveDurationMs) {
        List<OrderAllocation> scoped = scopedAllocations(allocations, scopedSupplyOrderIds);
        List<MasterPlanAllocationDto> allocationDtos = OrderAllocationConverter.toAllocationDtos(scoped);
        OptimizerResult result = new OptimizerResult(
                engineId,
                OrderAllocationConverter.toPlanningAssignmentsFromDtos(allocationDtos),
                scoreSummary,
                solveDurationMs,
                List.of(),
                allocationDtos);
        applyAllocationDtos(graph, rolEngine, allocationDtos);
        return result;
    }

    public void applyAllocationDtos(
            OntologyGraph graph,
            RolEngine rolEngine,
            List<MasterPlanAllocationDto> allocationDtos) {
        if (graph == null || allocationDtos == null || allocationDtos.isEmpty()) {
            return;
        }
        OperationPlannedTimeProjection.apply(graph, allocationDtos);
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        ResourceCapacityAssignmentProjection.apply(graph, allocationDtos, periodIndex);
        if (rolEngine != null) {
            ChangeSet changeSet = ontologyTimefoldMapper.toChangeSet(allocationDtos, graph, periodIndex);
            rolTransaction.apply(changeSet, graph, rolEngine);
        }
    }

    private static List<MasterPlanAllocationDto> scopedAllocationDtos(
            List<MasterPlanAllocationDto> allocations,
            Set<String> scopedSupplyOrderIds) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        if (scopedSupplyOrderIds == null || scopedSupplyOrderIds.isEmpty()) {
            return allocations;
        }
        List<MasterPlanAllocationDto> scoped = new ArrayList<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation != null && scopedSupplyOrderIds.contains(allocation.workOrderNo())) {
                scoped.add(allocation);
            }
        }
        return scoped;
    }

    private static List<OrderAllocation> scopedAllocations(
            List<OrderAllocation> allocations,
            Set<String> scopedSupplyOrderIds) {
        if (scopedSupplyOrderIds == null || scopedSupplyOrderIds.isEmpty()) {
            return allocations;
        }
        return allocations.stream()
                .filter(a -> scopedSupplyOrderIds.contains(a.getWorkOrderNo()))
                .collect(Collectors.toList());
    }
}
