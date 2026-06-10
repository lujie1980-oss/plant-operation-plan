package com.plantops.ontology;

import com.plantops.ontology.period.StandardResourcePeriod;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class OntologyLoaderSrpTest {

    private static final String RESOURCE_ID = "RES-OTD-SRP-1";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loadsSrpCapacityFromResourceCalendar() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph g = loader.loadForWorkspace(planningStart);
        StandardResourcePeriod srp = g.srp(OntologyIds.srpId(RESOURCE_ID, 0));
        assertNotNull(srp);
        assertEquals(960, srp.getTotalCapacity(), 1e-6);     // (480+0)+(420+60)
        assertEquals(60, srp.getCalendarDowntime(), 1e-6);
        assertEquals(900, srp.getAvailableCapacity(), 1e-6); // total - downtime

        // Out-of-horizon calendar row must not be clamped into the last bucket.
        int lastSeq = g.periodsOrdered().size() - 1;
        StandardResourcePeriod lastSrp = g.srp(OntologyIds.srpId(RESOURCE_ID, lastSeq));
        assertNotNull(lastSrp);
        assertEquals(0, lastSrp.getTotalCapacity(), 1e-6);

        // A period without calendar rows still gets a zero-capacity SRP.
        assertEquals(0, g.srp(OntologyIds.srpId(RESOURCE_ID, 1)).getTotalCapacity(), 1e-6);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (ProductionLineEntity.findByLineId("LINE-OTD-SRP-1") == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = "LINE-OTD-SRP-1";
            line.areaId = "AREA-OTD-SRP-1";
            line.resourceId = RESOURCE_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 100;
            line.stampWorkspace();
            line.persist();
        }

        if (ResourceCalendarEntity.findForResource(RESOURCE_ID).isEmpty()) {
            ResourceCalendarEntity shift1 = new ResourceCalendarEntity();
            shift1.resourceId = RESOURCE_ID;
            shift1.shiftId = "SHIFT-1";
            shift1.calendarDate = planningStart;
            shift1.availableCapacityMinutes = 480;
            shift1.unavailableCapacityMinutes = 0;
            shift1.stampWorkspace();
            shift1.persist();

            ResourceCalendarEntity shift2 = new ResourceCalendarEntity();
            shift2.resourceId = RESOURCE_ID;
            shift2.shiftId = "SHIFT-2";
            shift2.calendarDate = planningStart;
            shift2.availableCapacityMinutes = 420;
            shift2.unavailableCapacityMinutes = 60;
            shift2.stampWorkspace();
            shift2.persist();

            ResourceCalendarEntity farFuture = new ResourceCalendarEntity();
            farFuture.resourceId = RESOURCE_ID;
            farFuture.shiftId = "SHIFT-FUTURE";
            farFuture.calendarDate = planningStart.plusYears(1);
            farFuture.availableCapacityMinutes = 999;
            farFuture.unavailableCapacityMinutes = 0;
            farFuture.stampWorkspace();
            farFuture.persist();
        }
    }
}
