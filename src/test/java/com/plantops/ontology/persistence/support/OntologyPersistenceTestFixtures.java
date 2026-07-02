package com.plantops.ontology.persistence.support;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.demand.Demand;
import com.plantops.ontology.demand.DemandSourceType;
import com.plantops.ontology.fulfillment.Fulfillment;
import com.plantops.ontology.fulfillment.FulfillmentType;
import com.plantops.ontology.period.Period;
import com.plantops.ontology.period.PeriodGranularity;
import com.plantops.ontology.period.ProductInStockingPointPeriod;
import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.ontology.supply.Operation;
import com.plantops.ontology.supply.OperationOnStandardResource;
import com.plantops.ontology.supply.ResourceCapacityAssignment;
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

        String oosrId = OntologyIds.operationOnStandardResourceId(opId, "RES-PERS-01");
        OperationOnStandardResource oosr = new OperationOnStandardResource(
                oosrId, opId, "RES-PERS-01", 0, 0, 60.0);
        ResourceCapacityAssignment rca = new ResourceCapacityAssignment(
                "RCA-PERS-01", opId, oosrId, srpId, 120, 120, false, null);

        Period period = new Period(OntologyIds.periodId(0), 0, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
        period.setGranularity(PeriodGranularity.DAY);
        period.setLeaf(true);

        return OntologyGraph.builder()
                .demand(demand)
                .supplyOrder(so)
                .operation(op)
                .operationOnStandardResource(oosr)
                .fulfillment(ff)
                .pispPeriod(pispp)
                .standardResourcePeriod(srp)
                .resourceCapacityAssignment(rca)
                .periodsOrdered(java.util.List.of(period))
                .build();
    }

    /** Multi-entity P0 graph for PG extended parity (AC-PERS-01). */
    public static OntologyGraph extendedP0Graph() {
        OntologyGraph base = sampleP0Graph();
        String pispId2 = OntologyIds.pispId("FG-PERS-02");
        String demandId2 = "DEM-PERS-02";
        String soId2 = "SO-PERS-02";
        String opId2 = OntologyIds.operationId(soId2, 1);
        String pisppId2 = OntologyIds.pisppId(pispId2, 0);
        String srpId2 = OntologyIds.srpId("RES-PERS-02", 0);

        Demand demand2 = new Demand(
                demandId2, "FG-PERS-02", pispId2, 60.0,
                LocalDate.of(2026, 7, 15), 2,
                DemandSourceType.CUSTOMER_DELIVERY, "COLD-PERS-02");

        SupplyOrder so2 = new SupplyOrder(
                soId2, "FG-PERS-02", pispId2, 60.0,
                LocalDate.of(2026, 7, 12),
                SupplyOrderStatus.OPEN, SupplyOrderType.PLANNED_PRODUCTION);

        Operation op2 = new Operation(opId2, soId2, 1, "Pack");
        op2.setRoutingSequenceNo(20);
        op2.setProductionDuration(1800);

        Fulfillment ff2 = new Fulfillment(
                "FF-PERS-02", demandId2, soId2, 30.0, FulfillmentType.WORK_ORDER_PEG);

        ProductInStockingPointPeriod pispp2 = new ProductInStockingPointPeriod(
                pisppId2, pispId2, OntologyIds.periodId(0));
        pispp2.setOnHand(5);
        pispp2.setPlannedSupplyTotal(55);
        pispp2.setPlannedDemandQuantityTotal(60);
        pispp2.recalculatePlanningFields();

        StandardResourcePeriod srp2 = new StandardResourcePeriod(srpId2, "RES-PERS-02", OntologyIds.periodId(0));
        srp2.setTotalCapacity(240);
        srp2.setReservedCapacity(60);
        srp2.recalculateCapacityFields();

        String oosrId2 = OntologyIds.operationOnStandardResourceId(opId2, "RES-PERS-02");
        OperationOnStandardResource oosr2 = new OperationOnStandardResource(
                oosrId2, opId2, "RES-PERS-02", 0, 0, 30.0);
        ResourceCapacityAssignment rca2 = new ResourceCapacityAssignment(
                "RCA-PERS-02", opId2, oosrId2, srpId2, 60, 60, true, "PG-PERS-02");

        return OntologyGraph.builder()
                .demand(base.demandsById().values().iterator().next())
                .supplyOrder(base.supplyOrdersById().values().iterator().next())
                .operation(base.operationsById().values().iterator().next())
                .operationOnStandardResource(base.operationOnStandardResourceById().values().iterator().next())
                .fulfillment(base.fulfillments().getFirst())
                .pispPeriod(base.pispPeriodsById().values().iterator().next())
                .standardResourcePeriod(base.srpById().values().iterator().next())
                .resourceCapacityAssignment(base.resourceCapacityAssignmentsById().values().iterator().next())
                .demand(demand2)
                .supplyOrder(so2)
                .operation(op2)
                .operationOnStandardResource(oosr2)
                .fulfillment(ff2)
                .pispPeriod(pispp2)
                .standardResourcePeriod(srp2)
                .resourceCapacityAssignment(rca2)
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

        assertEquals(source.periodsOrdered().size(), restored.periodsOrdered().size());
        for (int i = 0; i < source.periodsOrdered().size(); i++) {
            Period expected = source.periodsOrdered().get(i);
            Period actual = restored.periodsOrdered().get(i);
            assertEquals(expected.getId(), actual.getId());
            assertEquals(expected.getSequenceNr(), actual.getSequenceNr());
            assertEquals(expected.getStartDate(), actual.getStartDate());
            assertEquals(expected.getEndDate(), actual.getEndDate());
            assertEquals(expected.getGranularity(), actual.getGranularity());
            assertEquals(expected.getShiftId(), actual.getShiftId());
            assertEquals(expected.getParentPeriodId(), actual.getParentPeriodId());
            assertEquals(expected.isLeaf(), actual.isLeaf());
        }

        assertEquals(source.srpById().size(), restored.srpById().size());
        for (StandardResourcePeriod srp : source.srpById().values()) {
            StandardResourcePeriod r = restored.srp(srp.getId());
            assertNotNull(r);
            assertEquals(srp.getTotalCapacity(), r.getTotalCapacity(), 1e-9);
            assertEquals(srp.getReservedCapacity(), r.getReservedCapacity(), 1e-9);
        }

        assertEquals(
                source.resourceCapacityAssignmentsById().size(),
                restored.resourceCapacityAssignmentsById().size());
        for (ResourceCapacityAssignment rca : source.resourceCapacityAssignmentsById().values()) {
            ResourceCapacityAssignment r = restored.resourceCapacityAssignment(rca.getId());
            assertNotNull(r, "rca " + rca.getId());
            assertEquals(rca.getOperationId(), r.getOperationId());
            assertEquals(rca.getOperationOnStandardResourceId(), r.getOperationOnStandardResourceId());
            assertEquals(rca.getStandardResourcePeriodId(), r.getStandardResourcePeriodId());
            assertEquals(rca.getAssignedMinutes(), r.getAssignedMinutes());
            assertEquals(rca.getOperationTotalMinutes(), r.getOperationTotalMinutes());
            assertEquals(rca.isLocked(), r.isLocked());
            assertEquals(rca.getParallelGroupId(), r.getParallelGroupId());
        }
    }
}
