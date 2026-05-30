package com.plantops.scenario.planning;

import com.plantops.persistence.entity.MasterPlanAllocationEntity;
import com.plantops.solver.masterplan.TimeSlot;
import com.plantops.solver.masterplan.TimeslotGranularity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MasterPlanSolutionRestorerTest {

    @Test
    void findMatchingSlot_byResourceDateShift() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("S0", 0, d, d, TimeslotGranularity.DAY, "DAY", "RES-A", 480),
                new TimeSlot("S1", 1, d.plusDays(1), d.plusDays(1), TimeslotGranularity.DAY, "DAY", "RES-B", 480));

        MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
        row.resourceId = "RES-B";
        row.slotDate = d.plusDays(1);
        row.shiftId = "DAY";
        row.slotIndex = 99;

        TimeSlot matched = MasterPlanSolutionRestorer.findMatchingSlot(slots, row);
        assertEquals("S1", matched.getId());
    }

    @Test
    void findMatchingSlot_fallbackToIndex() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        List<TimeSlot> slots = List.of(
                new TimeSlot("S0", 0, d, d, TimeslotGranularity.DAY, "DAY", "RES-A", 480));

        MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
        row.slotIndex = 0;

        assertEquals("S0", MasterPlanSolutionRestorer.findMatchingSlot(slots, row).getId());
    }

    @Test
    void findMatchingSlot_returnsNullWhenMissing() {
        MasterPlanAllocationEntity row = new MasterPlanAllocationEntity();
        row.resourceId = "UNKNOWN";
        row.slotDate = LocalDate.now();
        assertNull(MasterPlanSolutionRestorer.findMatchingSlot(List.of(), row));
    }
}
