package com.plantops.solver.masterplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterPlanCapacityOverlayTest {

    @Test
    void fixedLoadAggregatedBySlot() {
        MasterPlanCapacityOverlay overlay = MasterPlanCapacityOverlay.fromFixedLoads(
                List.of(new SlotFixedLoad("R1-D0", 100), new SlotFixedLoad("R1-D0", 50)),
                LocalDate.now());
        assertEquals(150, overlay.fixedMinutesForSlot("R1-D0"));
    }

    @Test
    void replanEligibilityRespectsCutoff() {
        LocalDate cutoff = LocalDate.of(2026, 6, 10);
        MasterPlanCapacityOverlay overlay = new MasterPlanCapacityOverlay(cutoff, java.util.Map.of());
        TimeSlot before = new TimeSlot("R1-D0", 0, cutoff, "DAY", "R1", 480);
        TimeSlot after = new TimeSlot("R1-D1", 1, cutoff.plusDays(1), "DAY", "R1", 480);
        assertFalse(overlay.isSlotEligibleForReplan(before));
        assertTrue(overlay.isSlotEligibleForReplan(after));
    }
}
