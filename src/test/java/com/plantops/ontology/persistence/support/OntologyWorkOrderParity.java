package com.plantops.ontology.persistence.support;

import com.plantops.ontology.persistence.OntologyEntityMapper;
import com.plantops.ontology.persistence.entity.OntEntityKey;
import com.plantops.ontology.persistence.entity.OntSupplyOrderEntity;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.WorkOrderSupplyOrderMapper;
import com.plantops.persistence.entity.WorkOrderEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** AC-PERS-04: compare legacy work_order with ont_supply_order on key fields. */
public final class OntologyWorkOrderParity {

    private OntologyWorkOrderParity() {}

    public static void assertWorkOrderMatchesSupplyOrder(WorkOrderEntity wo, SupplyOrder so) {
        SupplyOrder expected = WorkOrderSupplyOrderMapper.toSupplyOrder(wo);
        assertNotNull(expected);
        assertNotNull(so);
        assertEquals(expected.getId(), so.getId());
        assertEquals(expected.getProductCode(), so.getProductCode());
        assertEquals(expected.getPispId(), so.getPispId());
        assertEquals(expected.getQuantity(), so.getQuantity(), 1e-9);
        assertEquals(expected.getNeedDate(), so.getNeedDate());
        assertEquals(expected.getStatus(), so.getStatus());
        assertEquals(expected.getType(), so.getType());
    }

    public static void assertWorkOrderMatchesOntRow(
            WorkOrderEntity wo, OntSupplyOrderEntity row) {
        assertWorkOrderMatchesSupplyOrder(wo, OntologyEntityMapper.toSupplyOrder(row));
    }

    public static void assertSupplyOrdersAlignWithWorkOrders(
            String workspaceId, String revisionId, Iterable<WorkOrderEntity> workOrders) {
        for (WorkOrderEntity wo : workOrders) {
            OntSupplyOrderEntity row = OntSupplyOrderEntity.findById(
                    new OntEntityKey(workspaceId, revisionId, wo.workOrderNo));
            assertNotNull(row, "ont_supply_order missing for work_order " + wo.workOrderNo);
            assertWorkOrderMatchesOntRow(wo, row);
        }
    }
}
