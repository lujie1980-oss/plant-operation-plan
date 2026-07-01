package com.plantops.ontology;

import com.plantops.ontology.period.Period;
import com.plantops.ontology.scheduling.PeriodTimeSlotDeriver;
import com.plantops.solver.masterplan.TimeSlot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class OntologyLoaderSchedulingSlotTest {

    @Inject
    OntologyLoader loader;

    @Test
    void loadForWorkspaceDerivesTimeSlotsFromLeafPeriodsOnDemand() {
        LocalDate planningStart = LocalDate.of(2026, 6, 1);
        OntologyGraph graph = loader.loadForWorkspace(planningStart);

        List<TimeSlot> slots = PeriodTimeSlotDeriver.deriveTimeSlots(graph, null);

        if (!graph.periodsOrdered().isEmpty() && !graph.srpById().isEmpty()) {
            long leafPeriods = graph.periodsOrdered().stream().filter(Period::isLeaf).count();
            long resources = graph.srpById().values().stream()
                    .map(srp -> srp.getStandardResourceId())
                    .distinct()
                    .count();
            assertEquals(leafPeriods * resources, slots.size());
            assertFalse(slots.isEmpty());
        }
    }
}
