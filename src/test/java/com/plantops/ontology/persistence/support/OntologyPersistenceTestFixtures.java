package com.plantops.ontology.persistence.support;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.SupplyOrder;
import com.plantops.ontology.supply.SupplyOrderStatus;
import com.plantops.ontology.supply.SupplyOrderType;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class OntologyPersistenceTestFixtures {

    private OntologyPersistenceTestFixtures() {}

    public static OntologyGraph sampleP0Graph() {
        String pispId = OntologyIds.pispId("FG-PERS-01");
        String demandId = "DEM-PERS-01";
        String soId = "SO-PERS-01";
        String opId = OntologyIds.operationId(soId, 1);
        String supplyId = "SUP-PERS-01";
        String pisppId = OntologyIds.pisppId(pispId, 0);
        String srpId = OntologyIds.srpId("RES-PERS-01", 0);

        Demand demand = new Demand(
                demandId, "FG-PERS-01", pispId, 100.0,
                LocalDate.of(2026, 7, 1), 1,
                DemandSourceType.CUSTOMER_DELIVERY, "COLD-PERS-01");

        SupplyOrder so = new SupplyOrder(
                soId, "FG-PERS-01", pispId, 100.0,
                LocalDate.of(2026, 7, 5),
                SupplyOrderStatus.OPEN, SupplyOrderType.PLANNED_PRODUCTION);

        Operation op = new Operation(opId, soId, 1, "Assembly");
        op.setRoutingSequenceNo(10);
        op.setProductionDuration(3600);
        op.setPlannedStartTotal(LocalDateTime.of(2026, 7, 2, 8, 0));
        op.setPlannedEndTotal(LocalDateTime.of(2026, 7, 2, 9, 0));

        Fulfillment ff = new Fulfillment(
                "FF-PERS-01", demandId, supplyId, 50.0, FulfillmentType.WORK_ORDER_PEG);

        ProductInStockingPointPeriod pispp = new ProductInStockingPointPeriod(
                pisppId, pispId, OntologyIds.periodId(0));
        pispp.setOnHand(20);
        pispp.setPlannedSupplyTotal(80);
        pispp.setPlannedDemandQuantityTotal(100);
        pispp.recalculatePlanningFields();

        StandardResourcePeriod srp = new StandardResourcePeriod(srpId, "RES-PERS-01", OntologyIds.periodId(0));
        srp.setTotalCapacity(480);
        srp.setReservedCapacity(120);
        srp.recalculateCapacityFields();

        return OntologyGraph.builder()
                .demand(demand)
                .supplyOrder(so)
                .operation(op)
                .fulfillment(ff)
                .pispPeriod(pispp)
                .standardResourcePeriod(srp)
                .build();
    }

    public static void assertP0Parity(OntologyGraph source, OntologyGraph restored) {
        assertEquals(source.demandsById().size(), restored.demandsById().size());
        for (Demand d : source.demandsById().values()) {
            Demand r = restored.demand(d.getId());
            assertNotNull(r, "demand " + d.getId());
            assertEquals(d.getProductCode(), r.getProductCode());
            assertEquals(d.getPispId(), r.getPispId());
            assertEquals(d.getQuantity(), r.getQuantity(), 1e-9);
            assertEquals(d.getNeedDate(), r.getNeedDate());
            assertEquals(d.getPriority(), r.getPriority());
            assertEquals(d.getSourceType(), r.getSourceType());
            assertEquals(d.getSourceId(), r.getSourceId());
        }

        assertEquals(source.supplyOrdersById().size(), restored.supplyOrdersById().size());
        for (SupplyOrder so : source.supplyOrdersById().values()) {
            SupplyOrder r = restored.supplyOrder(so.getId());
            assertNotNull(r);
            assertEquals(so.getProductCode(), r.getProductCode());
            assertEquals(so.getQuantity(), r.getQuantity(), 1e-9);
            assertEquals(so.getStatus(), r.getStatus());
            assertEquals(so.getType(), r.getType());
        }

        assertEquals(source.operationsById().size(), restored.operationsById().size());
        for (Operation op : source.operationsById().values()) {
            Operation r = restored.operation(op.getId());
            assertNotNull(r);
            assertEquals(op.getSupplyOrderId(), r.getSupplyOrderId());
            assertEquals(op.getRoutingSequenceNo(), r.getRoutingSequenceNo());
            assertEquals(op.getProductionDuration(), r.getProductionDuration());
            assertEquals(op.getPlannedStartTotal(), r.getPlannedStartTotal());
            assertEquals(op.isInfeasible(), r.isInfeasible());
        }

        assertEquals(source.fulfillments().size(), restored.fulfillments().size());
        for (Fulfillment ff : source.fulfillments()) {
            Fulfillment r = restored.fulfillments().stream()
                    .filter(x -> ff.getId().equals(x.getId()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(r);
            assertEquals(ff.getDemandId(), r.getDemandId());
            assertEquals(ff.getSupplyId(), r.getSupplyId());
            assertEquals(ff.getQuantity(), r.getQuantity(), 1e-9);
            assertEquals(ff.getType(), r.getType());
        }

        assertEquals(source.pispPeriodsById().size(), restored.pispPeriodsById().size());
        for (ProductInStockingPointPeriod p : source.pispPeriodsById().values()) {
            ProductInStockingPointPeriod r = restored.pispPeriod(p.getId());
            assertNotNull(r);
            assertEquals(p.getOnHand(), r.getOnHand(), 1e-9);
            assertEquals(p.getPlannedSupplyTotal(), r.getPlannedSupplyTotal(), 1e-9);
            assertEquals(p.getPlannedDemandQuantityTotal(), r.getPlannedDemandQuantityTotal(), 1e-9);
        }

        assertEquals(source.srpById().size(), restored.srpById().size());
        for (StandardResourcePeriod srp : source.srpById().values()) {
            StandardResourcePeriod r = restored.srp(srp.getId());
            assertNotNull(r);
            assertEquals(srp.getTotalCapacity(), r.getTotalCapacity(), 1e-9);
            assertEquals(srp.getReservedCapacity(), r.getReservedCapacity(), 1e-9);
        }
    }
}
