package com.plantops.ontology.persistence;

import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.persistence.entity.OntDemandEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OntologyEntityMapperTest {

    @Test
    void demandRoundTripPreservesFields() {
        Demand original = new Demand(
                "DEM-1", "FG-1", "PISP-FG-1-DEFAULT-FG", 42.5,
                LocalDate.of(2026, 8, 1), 2,
                DemandSourceType.FORECAST, "FD-1");

        OntDemandEntity row = OntologyEntityMapper.fromDemand(original, "ws-1", "REV-1");
        Demand restored = OntologyEntityMapper.toDemand(row);

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getProductCode(), restored.getProductCode());
        assertEquals(original.getQuantity(), restored.getQuantity());
        assertEquals(original.getSourceType(), restored.getSourceType());
        assertEquals("ws-1", row.workspaceId);
        assertEquals("REV-1", row.revisionId);
    }
}
