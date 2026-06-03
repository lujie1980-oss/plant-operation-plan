package com.plantops.solver.masterplan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasterPlanCapacityConcentrationTest {

    @Test
    void activeSlotPenalty_scalesWithCapacityAndWeight() {
        assertEquals(480, MasterPlanCapacityConcentration.activeSlotPenalty(480, 1));
        assertEquals(960, MasterPlanCapacityConcentration.activeSlotPenalty(480, 2));
        assertEquals(0, MasterPlanCapacityConcentration.activeSlotPenalty(480, 0));
    }

    @Test
    void unusedCapacityPenalty_spareMinutesTimesWeight() {
        assertEquals(120, MasterPlanCapacityConcentration.unusedCapacityPenalty(480, 0, 360, 1));
        assertEquals(0, MasterPlanCapacityConcentration.unusedCapacityPenalty(480, 0, 480, 1));
        assertEquals(0, MasterPlanCapacityConcentration.unusedCapacityPenalty(480, 0, 0, 1));
    }

    @Test
    void unusedCapacityPenalty_respectsFixedOverlay() {
        assertEquals(80, MasterPlanCapacityConcentration.unusedCapacityPenalty(480, 100, 300, 1));
    }
}
