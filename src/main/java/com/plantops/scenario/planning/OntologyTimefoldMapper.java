package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.period.PeriodIndex;
import com.plantops.ontology.supply.ResourceCapacityAssignmentRollup;
import com.plantops.rol.ChangeOperation;
import com.plantops.rol.ChangeSet;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OntologyTimefoldMapper {

    private static final String PROPERTY_PLANNED_SUPPLY_TOTAL_OPTIMIZED = "plannedSupplyTotalOptimized";

    public ChangeSet toChangeSet(
            List<MasterPlanAllocationDto> allocations,
            OntologyGraph graph,
            PeriodIndex periodIndex) {
        if (allocations == null || allocations.isEmpty()) {
            return new ChangeSet(List.of(), "No allocations to project");
        }
        Map<String, Double> supplyByPisppId = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.productCode() == null || allocation.productCode().isBlank()) {
                continue;
            }
            String pispId = OntologyIds.pispId(allocation.productCode());
            if (graph.pisp(pispId) == null) {
                continue;
            }
            int sequenceNr = periodIndex.sequenceFor(resolvePlannedDate(allocation));
            String pisppId = OntologyIds.pisppId(pispId, sequenceNr);
            if (graph.pispPeriod(pisppId) == null) {
                continue;
            }
            double quantity = allocation.quantity() != null ? allocation.quantity().doubleValue() : 0.0;
            supplyByPisppId.merge(pisppId, Math.abs(quantity), Double::sum);
        }

        List<ChangeOperation> operations = new ArrayList<>(supplyByPisppId.size());
        for (Map.Entry<String, Double> entry : supplyByPisppId.entrySet()) {
            operations.add(new ChangeOperation(
                    ChangeOperation.TARGET_PRODUCT_IN_STOCKING_POINT_PERIOD,
                    entry.getKey(),
                    PROPERTY_PLANNED_SUPPLY_TOTAL_OPTIMIZED,
                    entry.getValue()));
        }

        Map<String, Double> reservedBySrpId = ResourceCapacityAssignmentRollup.reservedMinutesBySrpId(graph);
        if (reservedBySrpId.isEmpty()) {
            reservedBySrpId = reservedMinutesFromAllocations(allocations, graph, periodIndex);
        }
        for (Map.Entry<String, Double> entry : reservedBySrpId.entrySet()) {
            operations.add(new ChangeOperation(
                    ChangeOperation.TARGET_STANDARD_RESOURCE_PERIOD,
                    entry.getKey(), "reservedCapacity", entry.getValue()));
        }
        return new ChangeSet(operations, "Project allocations into PISPP supply");
    }

    private static Map<String, Double> reservedMinutesFromAllocations(
            List<MasterPlanAllocationDto> allocations,
            OntologyGraph graph,
            PeriodIndex periodIndex) {
        Map<String, Double> reservedBySrpId = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.resourceId() == null || allocation.resourceId().isBlank()) {
                continue;
            }
            int seq = periodIndex.sequenceFor(resolvePlannedDate(allocation), allocation.shiftId());
            String srpId = OntologyIds.srpId(allocation.resourceId(), seq);
            if (graph.srp(srpId) == null) {
                continue;
            }
            reservedBySrpId.merge(srpId, (double) allocation.durationMinutes(), Double::sum);
        }
        return reservedBySrpId;
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
