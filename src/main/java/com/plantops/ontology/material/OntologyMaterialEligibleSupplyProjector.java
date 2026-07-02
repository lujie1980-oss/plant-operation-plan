package com.plantops.ontology.material;

import com.plantops.api.dto.materialplanning.MaterialReservationDtos.EligibleSupplyListDto;
import com.plantops.api.dto.materialplanning.MaterialReservationDtos.EligibleSupplyRowDto;
import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.supply.Supply;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class OntologyMaterialEligibleSupplyProjector {

    public EligibleSupplyListDto project(OntologyGraph graph, String demandId) {
        Demand demand = graph.demand(demandId);
        if (demand == null) {
            throw new NotFoundException("Demand not found: " + demandId);
        }
        List<EligibleSupplyRowDto> rows = new ArrayList<>();
        for (Supply supply : OntologyMaterialReservationSupport.suppliesForPisp(graph, demand.getPispId())) {
            if (!demand.getProductCode().equals(supply.getProductCode())) {
                continue;
            }
            if (OntologyMaterialReservationSupport.isShortageSupply(supply.getId())) {
                continue;
            }
            OntologyMaterialReservationSupport.SupplyAvailability availability =
                    OntologyMaterialReservationSupport.resolveSupplyAvailability(graph, supply);
            double pegged = OntologyMaterialReservationSupport.peggedQtyForSupply(graph, supply.getId());
            double unpegged = OntologyMaterialReservationSupport.unpeggedQtyForSupply(
                    graph, supply, availability.availableQty());
            if (unpegged <= 0) {
                continue;
            }
            rows.add(new EligibleSupplyRowDto(
                    supply.getId(),
                    availability.supplyType(),
                    availability.availableDate(),
                    availability.availableQty(),
                    pegged,
                    unpegged));
        }
        rows.sort(Comparator
                .comparing(EligibleSupplyRowDto::availableDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EligibleSupplyRowDto::supplyType)
                .thenComparing(EligibleSupplyRowDto::supplyId));
        return new EligibleSupplyListDto(demandId, List.copyOf(rows));
    }
}
