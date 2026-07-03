package com.plantops.ontology;

import com.plantops.ontology.master.ProductInStockingPoint;
import com.plantops.ontology.master.StockingPoint;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.WorkOrderEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OntologyLoaderTest {

    @Inject
    OntologyLoader ontologyLoader;

    @Test
    void workOrderSupplyOrderMapperMapsCoreFields() {
        WorkOrderEntity wo = new WorkOrderEntity();
        wo.workOrderNo = "WO-TEST-001";
        wo.productCode = "FG-100";
        wo.quantity = new BigDecimal("250.5");

        SupplyOrder supplyOrder = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);

        assertNotNull(supplyOrder);
        assertEquals("WO-TEST-001", supplyOrder.getId());
        assertEquals("FG-100", supplyOrder.getProductCode());
        assertEquals(250.5, supplyOrder.getQuantity(), 1e-6);
        assertEquals(OntologyIds.pispId("FG-100"), supplyOrder.getPispId());
    }

    @Test
    void loadForWorkspaceBuildsSyntheticPispsAndSupplyOrders() {
        OntologyGraph graph = ontologyLoader.loadForWorkspace(LocalDate.of(2026, 6, 7));

        assertEquals(OntologyIds.DEFAULT_PERIOD_COUNT, graph.periodsOrdered().size());
        assertEquals(OntologyIds.periodId(0), graph.periodsOrdered().getFirst().getId());
        assertEquals(LocalDate.of(2026, 6, 7), graph.periodsOrdered().getFirst().getStartDate());

        assertFalse(graph.pispsById().isEmpty(), "expected at least one synthetic PISP from sample data");

        for (ProductInStockingPoint pisp : graph.pispsById().values()) {
            assertEquals(
                    OntologyIds.pispId(pisp.getProductCode()),
                    pisp.getId(),
                    "PISP id must follow PISP-{productCode}-DEFAULT-FG");
            assertEquals(StockingPoint.DEFAULT_FG, pisp.getStockingPointId());
            assertEquals(OntologyIds.DEFAULT_PERIOD_COUNT, countPisppForPisp(graph, pisp.getId()));
        }

        for (SupplyOrder supplyOrder : graph.supplyOrdersById().values()) {
            assertNotNull(graph.supplyOrder(supplyOrder.getId()));
            assertEquals(supplyOrder.getId(), supplyOrder.getId());
            assertTrue(graph.pisp(supplyOrder.getPispId()) != null);
        }
    }

    private static int countPisppForPisp(OntologyGraph graph, String pispId) {
        int count = 0;
        for (var pispp : graph.pispPeriodsById().values()) {
            if (pispId.equals(pispp.getPispId())) {
                count++;
            }
        }
        return count;
    }
}
