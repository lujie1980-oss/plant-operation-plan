package com.plantops.ontology.period;

import com.plantops.ontology.OntologyGraph;
import com.plantops.ontology.OntologyIds;
import com.plantops.ontology.OntologyLoader;
import com.plantops.persistence.entity.ProductionLineEntity;
import com.plantops.persistence.entity.ResourceCalendarEntity;
import com.plantops.workspace.WorkspaceResolver;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class PhysicalResourcePeriodLoaderIntegrationTest {

    private static final String SR_ID = "RES-PRP-ROLLUP-SR";
    private static final String LINE_A = "LINE-PRP-A";
    private static final String LINE_B = "LINE-PRP-B";

    @Inject
    OntologyLoader loader;

    @Test
    @TestTransaction
    void loaderRollsUpTwoLinesToSingleSrp() {
        LocalDate planningStart = LocalDate.now();
        ensureFixture(planningStart);

        OntologyGraph graph = loader.loadForWorkspace(planningStart);
        String periodId = graph.periodsOrdered().getFirst().getId();

        PhysicalResourcePeriod prpA = graph.prp(OntologyIds.prpId(LINE_A, periodId));
        PhysicalResourcePeriod prpB = graph.prp(OntologyIds.prpId(LINE_B, periodId));
        StandardResourcePeriod srp = graph.srp(OntologyIds.srpId(SR_ID, 0));

        assertNotNull(prpA);
        assertNotNull(prpB);
        assertNotNull(srp);
        assertEquals(480, prpA.getAvailableCapacity(), 1e-6);
        assertEquals(360, prpB.getAvailableCapacity(), 1e-6);
        assertEquals(
                prpA.getAvailableCapacity() + prpB.getAvailableCapacity(),
                srp.getTotalCapacity(),
                1e-6);
        assertEquals(840, srp.getAvailableCapacity(), 1e-6);
    }

    private void ensureFixture(LocalDate planningStart) {
        if (ProductionLineEntity.findByLineId(LINE_A) == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = LINE_A;
            line.areaId = "AREA-PRP";
            line.resourceId = SR_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 480;
            line.stampWorkspace();
            line.persist();
        }
        if (ProductionLineEntity.findByLineId(LINE_B) == null) {
            ProductionLineEntity line = new ProductionLineEntity();
            line.lineId = LINE_B;
            line.areaId = "AREA-PRP";
            line.resourceId = SR_ID;
            line.lineMinHeadcount = 1;
            line.lineCapacityPerShift = 360;
            line.stampWorkspace();
            line.persist();
        }

        upsertCalendar(LINE_A, planningStart, 480, 0);
        upsertCalendar(LINE_B, planningStart, 360, 0);
    }

    private void upsertCalendar(String resourceId, LocalDate date, int available, int unavailable) {
        ResourceCalendarEntity existing = ResourceCalendarEntity.find(
                        "workspaceId = ?1 and resourceId = ?2 and calendarDate = ?3 and shiftId = ?4",
                        WorkspaceResolver.currentWorkspaceId(),
                        resourceId,
                        date,
                        "SHIFT-1")
                .firstResult();
        if (existing != null) {
            existing.availableCapacityMinutes = available;
            existing.unavailableCapacityMinutes = unavailable;
            return;
        }
        ResourceCalendarEntity cal = new ResourceCalendarEntity();
        cal.resourceId = resourceId;
        cal.shiftId = "SHIFT-1";
        cal.calendarDate = date;
        cal.availableCapacityMinutes = available;
        cal.unavailableCapacityMinutes = unavailable;
        cal.stampWorkspace();
        cal.persist();
    }
}
