package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.rol.RolEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
        applySrpReserved(graph, allocations, rolEngine);
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

    private static void applySrpReserved(
            OntologyGraph graph,
            List<MasterPlanAllocationDto> allocations,
            RolEngine rolEngine) {
        PeriodIndex periodIndex = PeriodIndex.of(graph.periodsOrdered());
        Map<String, Double> reservedBySrpId = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.resourceId() == null || allocation.resourceId().isBlank()) {
                continue;
            }
            LocalDate plannedDate = resolvePlannedDate(allocation);
            if (plannedDate == null) {
                continue;
            }
            int seq = periodIndex.sequenceFor(plannedDate);
            String srpId = OntologyIds.srpId(allocation.resourceId(), seq);
            if (graph.srp(srpId) == null) {
                continue;
            }
            reservedBySrpId.merge(srpId, (double) allocation.durationMinutes(), Double::sum);
        }
        for (Map.Entry<String, Double> entry : reservedBySrpId.entrySet()) {
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

    private static LocalDate resolvePlannedDate(MasterPlanAllocationDto allocation) {
        if (allocation.slotDate() != null) {
            return allocation.slotDate();
        }
        if (allocation.plannedEndTs() != null) {
            return allocation.plannedEndTs().toLocalDate();
        }
        if (allocation.plannedStartTs() != null) {
            return allocation.plannedStartTs().toLocalDate();
        }
        return null;
    }
}
