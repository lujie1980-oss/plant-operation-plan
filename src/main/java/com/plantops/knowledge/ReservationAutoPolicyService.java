package com.plantops.knowledge;

import com.plantops.api.dto.materialplanning.MaterialReservationDtos.EligibleSupplyRowDto;
import com.plantops.ontology.demand.Demand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Comparator;

/** RULE-FF-06：从 Effective 读取 `reservation_auto_policy`（TODO-15 K3）。 */
@ApplicationScoped
public class ReservationAutoPolicyService {

    public enum PolicyMode {
        DEFAULT,
        DATE_FIRST
    }

    @Inject
    KnowledgeContext knowledgeContext;

    public PolicyMode mode() {
        String raw = knowledgeContext.getParameter("reservation_auto_policy");
        if (raw == null || raw.isBlank() || "DEFAULT".equalsIgnoreCase(raw.trim())) {
            return PolicyMode.DEFAULT;
        }
        if ("DATE_FIRST".equalsIgnoreCase(raw.trim())) {
            return PolicyMode.DATE_FIRST;
        }
        return PolicyMode.DEFAULT;
    }

    public Comparator<EligibleSupplyRowDto> compareSuppliesForDemandAnchor() {
        return switch (mode()) {
            case DATE_FIRST -> Comparator
                    .comparing(EligibleSupplyRowDto::availableDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(row -> !"INVENTORY".equals(row.supplyType()))
                    .thenComparing(EligibleSupplyRowDto::unpeggedQty, Comparator.reverseOrder());
            case DEFAULT -> Comparator
                    .comparing((EligibleSupplyRowDto row) -> !"INVENTORY".equals(row.supplyType()))
                    .thenComparing(EligibleSupplyRowDto::availableDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(EligibleSupplyRowDto::unpeggedQty, Comparator.reverseOrder());
        };
    }

    public Comparator<Demand> compareDemandsForSupplyAnchor() {
        return Comparator
                .comparing(Demand::getNeedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Comparator.comparingInt(Demand::getPriority).reversed())
                .thenComparing(Demand::getId);
    }
}
