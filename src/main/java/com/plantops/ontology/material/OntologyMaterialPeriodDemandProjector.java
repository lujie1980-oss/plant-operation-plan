package com.plantops.ontology.material;

import com.plantops.api.dto.materialplanning.MaterialReservationDtos.PeriodDemandListDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.PeriodDemandRowDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.Demand;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OntologyMaterialPeriodDemandProjector {

    public PeriodDemandListDto project(
            OntologyGraph graph,
            String pispId,
            String periodFrom,
            String periodTo) {
        OntologyMaterialSupplyRoutingService.requirePisp(graph, pispId);
        List<PeriodDemandRowDto> rows = new ArrayList<>();
        for (Demand demand : OntologyMaterialReservationSupport.demandsForPisp(graph, pispId)) {
            if (!OntologyMaterialReservationSupport.dateInPeriodRange(
                    graph, demand.getNeedDate(), periodFrom, periodTo)) {
                continue;
            }
            double pegged = OntologyMaterialReservationSupport.peggedQtyForDemand(graph, demand.getId());
            rows.add(new PeriodDemandRowDto(
                    demand.getId(),
                    demand.getSourceType().name(),
                    demand.getNeedDate(),
                    demand.getQuantity(),
                    pegged,
                    OntologyMaterialReservationSupport.unpeggedQtyForDemand(graph, demand),
                    demand.getPispId(),
                    OntologyMaterialReservationSupport.periodIdForDate(graph, demand.getNeedDate())));
        }
        rows.sort(Comparator
                .comparing(PeriodDemandRowDto::needDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PeriodDemandRowDto::demandId));
        return new PeriodDemandListDto(pispId, periodFrom, periodTo, List.copyOf(rows));
    }
}
