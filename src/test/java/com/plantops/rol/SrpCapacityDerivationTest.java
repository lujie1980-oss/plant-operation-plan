package com.plantops.rol;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.period.StandardResourcePeriod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SrpCapacityDerivationTest {

    @Test
    void freeCapacityIsAvailableMinusReserved() {
        var srp = new StandardResourcePeriod("SRP-1", "RES-1", "P-0");
        srp.setTotalCapacity(480);
        srp.setCalendarDowntime(60);
        srp.setTechnicalDowntime(0);
        srp.setReservedCapacity(120);
        srp.recalculateCapacityFields();
        assertEquals(420, srp.getAvailableCapacity(), 1e-6);
        assertEquals(300, srp.getFreeCapacity(), 1e-6);
    }

    @Test
    void overloadWhenReservedExceedsAvailable() {
        var srp = new StandardResourcePeriod("SRP-2", "RES-1", "P-0");
        srp.setTotalCapacity(100);
        srp.setReservedCapacity(130);
        srp.recalculateCapacityFields();
        assertEquals(30, srp.getOverloadCapacity(), 1e-6);
        assertEquals(-30, srp.getFreeCapacity(), 1e-6);
    }

    @Test
    void rolEnginePropagatesWhenReservedCapacityChanges() {
        var srp = new StandardResourcePeriod("SRP-1", "RES-1", "P-0");
        srp.setTotalCapacity(480);
        srp.setCalendarDowntime(60);
        srp.setTechnicalDowntime(0);
        srp.setReservedCapacity(0);
        srp.recalculateCapacityFields();

        OntologyGraph graph = OntologyGraph.builder().standardResourcePeriod(srp).build();
        RolEngine engine = RolEngine.withSrpCapacityRules(graph);

        engine.applyPropertyChange(srp, "reservedCapacity", 120.0);

        assertEquals(420, srp.getAvailableCapacity(), 1e-6);
        assertEquals(300, srp.getFreeCapacity(), 1e-6);
        assertEquals(0, srp.getOverloadCapacity(), 1e-6);
    }
}
