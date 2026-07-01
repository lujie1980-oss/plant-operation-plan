package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.ResourceCapacityAssignmentProjection;
import com.plantops.ontology.supply.ResourceCapacityAssignmentRollup;
import com.plantops.rol.RolEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 从已发布 {@code planVersionId} 的 {@link com.plantops.persistence.entity.MasterPlanAllocationEntity}
 * 反灌 Session 图：工序计划时间 + locked，以及 SRP {@code reservedCapacity}。
 */
@ApplicationScoped
public class PlanVersionAllocationHydrator {

    @Inject
    com.plantops.scenario.MasterPlanService masterPlanService;

    public void hydrate(OntologyGraph graph, String planVersionId) {
        hydrate(graph, planVersionId, null);
    }

    public void hydrate(OntologyGraph graph, String planVersionId, RolEngine rolEngine) {
        if (graph == null || planVersionId == null || planVersionId.isBlank()) {
            return;
        }
        List<MasterPlanAllocationDto> allocations = masterPlanService.allocationsForPlanVersion(planVersionId);
        if (allocations.isEmpty()) {
            return;
        }
        OperationPlannedTimeProjection.apply(graph, allocations);
        lockAllocatedOperations(graph, allocations);
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        ResourceCapacityAssignmentProjection.apply(graph, allocations, periodIndex);
        applySrpReserved(graph, rolEngine);
    }

    private static void lockAllocatedOperations(
            OntologyGraph graph,
            List<MasterPlanAllocationDto> allocations) {
        Set<String> workOrdersWithAlloc = allocations.stream()
                .map(MasterPlanAllocationDto::workOrderNo)
                .filter(wo -> wo != null && !wo.isBlank())
                .collect(Collectors.toSet());
        for (String supplyOrderId : workOrdersWithAlloc) {
            for (Operation operation : graph.operationsForSupplyOrder(supplyOrderId)) {
                if (operation.getPlannedStartTotal() != null) {
                    operation.setLocked(true);
                }
            }
        }
    }

    private static void applySrpReserved(OntologyGraph graph, RolEngine rolEngine) {
        for (Map.Entry<String, Double> entry : ResourceCapacityAssignmentRollup.reservedMinutesBySrpId(graph).entrySet()) {
            StandardResourcePeriod srp = graph.srp(entry.getKey());
            if (srp == null) {
                continue;
            }
            if (rolEngine != null) {
                rolEngine.applyPropertyChange(srp, "reservedCapacity", entry.getValue());
            } else {
                srp.setReservedCapacity(entry.getValue());
                srp.recalculateCapacityFields();
            }
        }
    }
}
