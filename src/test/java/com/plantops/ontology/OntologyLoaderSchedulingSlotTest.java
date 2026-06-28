package com.plantops.ontology;

import com.plantops.ontology.scheduling.PeriodTimeSlotAlignment;
import com.plantops.ontology.scheduling.SchedulingSlot;
import com.plantops.persistence.entity.ProductionResourceEntity;
import com.plantops.scenario.TimeslotHorizonService;
import com.plantops.solver.masterplan.TimeSlot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class OntologyLoaderSchedulingSlotTest {

    @Inject
    OntologyLoader loader;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Test
    void loadForWorkspaceIncludesAlignedSchedulingSlots() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        List<SchedulingSlot> ontologySlots = graph.schedulingSlotsOrdered();
        List<TimeSlot> horizonSlots = timeslotHorizonService.buildSlots(
                planningStart, ProductionResourceEntity.routingResourceIds());

        assertFalse(ontologySlots.isEmpty());
        PeriodTimeSlotAlignment.assertAligned(ontologySlots, horizonSlots);
    }
}
