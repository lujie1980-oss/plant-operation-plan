package com.plantops.scenario.planning;

import com.plantops.api.dto.MasterPlanAllocationDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.rol.ChangeOperation;
import com.plantops.rol.ChangeSet;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OntologyTimefoldMapper {

    private static final String PROPERTY_PLANNED_SUPPLY_TOTAL = "plannedSupplyTotal";

    public ChangeSet toChangeSet(
            List<MasterPlanAllocationDto> allocations,
            OntologyGraph graph,
            LocalDate planningStart) {
        if (allocations == null || allocations.isEmpty()) {
            return new ChangeSet(List.of(), "No allocations to project");
        }
        LocalDate effectivePlanningStart = planningStart != null ? planningStart : LocalDate.now();
        Map<String, Double> supplyByPisppId = new LinkedHashMap<>();
        for (MasterPlanAllocationDto allocation : allocations) {
            if (allocation == null || allocation.productCode() == null || allocation.productCode().isBlank()) {
                continue;
            }
            String pispId = OntologyIds.pispId(allocation.productCode());
            if (graph.pisp(pispId) == null) {
                continue;
            }
            int periodIndex = periodIndexForDate(resolvePlannedDate(allocation), effectivePlanningStart);
            String pisppId = OntologyIds.pisppId(pispId, periodIndex);
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
                    PROPERTY_PLANNED_SUPPLY_TOTAL,
                    entry.getValue()));
        }
        return new ChangeSet(operations, "Project allocations into PISPP supply");
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

    private static int periodIndexForDate(LocalDate date, LocalDate planningStart) {
        if (date == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(planningStart, date);
        if (days < 0) {
            return 0;
        }
        if (days >= OntologyIds.DEFAULT_PERIOD_COUNT) {
            return OntologyIds.DEFAULT_PERIOD_COUNT - 1;
        }
        return (int) days;
    }
}
