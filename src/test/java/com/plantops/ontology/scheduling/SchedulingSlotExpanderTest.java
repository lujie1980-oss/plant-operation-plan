package com.plantops.ontology.scheduling;

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
class SchedulingSlotExpanderTest {

    @Inject
    SchedulingSlotExpander expander;

    @Inject
    TimeslotHorizonService timeslotHorizonService;

    @Test
    void expandMatchesHorizonServiceSlotCountAndAlignment() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        var resourceIds = ProductionResourceEntity.routingResourceIds();

        List<TimeSlot> horizonSlots = timeslotHorizonService.buildSlots(start, resourceIds);
        List<SchedulingSlot> ontologySlots = expander.expand(start, resourceIds);

        assertFalse(horizonSlots.isEmpty());
        PeriodTimeSlotAlignment.assertAligned(ontologySlots, horizonSlots);
    }
}
