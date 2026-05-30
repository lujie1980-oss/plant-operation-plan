package com.plantops.solver.masterplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderTimingBoundsContextTest {

    @Test
    void slotBeforeEarliestIsViolation() {
        LocalDateTime earliest = LocalDate.of(2026, 6, 10).atTime(8, 0);
        WorkOrderTimingBoundsContext bounds = new WorkOrderTimingBoundsContext(
                Map.of("WO-1", earliest));
        TimeSlot tooEarly = new TimeSlot("R1-D0", 0, LocalDate.of(2026, 6, 9), "DAY", "R1", 480);
        TimeSlot ok = new TimeSlot("R1-D1", 1, LocalDate.of(2026, 6, 10), "DAY", "R1", 480);

        assertTrue(bounds.violatesEarliestStart("WO-1", tooEarly));
        assertFalse(bounds.violatesEarliestStart("WO-1", ok));
        assertFalse(bounds.slotAllowed("WO-1", tooEarly));
        assertTrue(bounds.slotAllowed("WO-1", ok));
        assertTrue(bounds.violationWeight("WO-1", tooEarly) >= 24 * 60);
    }

    @Test
    void unknownWorkOrderAllowsAnySlot() {
        WorkOrderTimingBoundsContext bounds = WorkOrderTimingBoundsContext.empty();
        TimeSlot slot = new TimeSlot("R1-D0", 0, LocalDate.now(), "DAY", "R1", 480);
        assertFalse(bounds.violatesEarliestStart("WO-X", slot));
        assertTrue(bounds.slotAllowed("WO-X", slot));
        assertEquals(0, bounds.violationWeight("WO-X", slot));
    }
}
