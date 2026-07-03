package com.plantops.ontology.master;

import com.plantops.persistence.entity.BomComponentEntity;
import com.plantops.persistence.entity.MaterialEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BomStockingPointTierResolverTest {

    private static final String FG = "FG-TIER-TEST";
    private static final String SFG_B = "SFG-B-TIER-TEST";
    private static final String SFG_A = "SFG-A-TIER-TEST";
    private static final String RAW = "RAW-TIER-TEST";

    @Inject
    BomStockingPointTierResolver resolver;

    @Test
    @TestTransaction
    void assignsFourStockingPointTiersFromBom() {
        ensureFourLevelBom();

        var assignments = resolver.assignAllMaterials();

        assertEquals(StockingPoint.FG, assignments.get(FG).stockingPointId());
        assertEquals(StockingPoint.SFG_B, assignments.get(SFG_B).stockingPointId());
        assertEquals(StockingPoint.SFG_A, assignments.get(SFG_A).stockingPointId());
        assertEquals(StockingPoint.RAW, assignments.get(RAW).stockingPointId());
        assertEquals(4, resolver.orderedStockingPoints().size());
        assertTrue(resolver.orderedStockingPoints().stream().anyMatch(sp -> StockingPoint.RAW.equals(sp.getId())));
    }

    private static void ensureFourLevelBom() {
        for (String code : new String[] { FG, SFG_B, SFG_A, RAW }) {
            if (MaterialEntity.findByCode(code) == null) {
                MaterialEntity m = new MaterialEntity();
                m.materialCode = code;
                m.materialName = code;
                m.stampWorkspace();
                m.persist();
            }
        }
        persistBom(FG, FG, SFG_B);
        persistBom(FG, SFG_B, SFG_A);
        persistBom(FG, SFG_A, RAW);
    }

    private static void persistBom(String finished, String parent, String component) {
        if (!BomComponentEntity.findByFinishedAndParent(finished, parent).stream()
                .anyMatch(b -> component.equals(b.componentProductCode))) {
            BomComponentEntity bom = new BomComponentEntity();
            bom.bomId = "BOM-TIER";
            bom.bomVersion = "1";
            bom.finishedProductCode = finished;
            bom.parentProductCode = parent;
            bom.componentProductCode = component;
            bom.componentQty = BigDecimal.ONE;
            bom.isCriticalComponent = true;
            bom.stampWorkspace();
            bom.persist();
        }
    }
}
