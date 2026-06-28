package com.plantops.solver.masterplan;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterPlanCalendarMomentsTest {

    @Test
    void sameCalendarDayDifferentSlotIndexSharesDayOrdinal() {
        LocalDate day = LocalDate.of(2026, 6, 10);
        TimeSlot slotA = new TimeSlot("RES-A-D0", 0, day, "DAY", "RES-A", 480);
        TimeSlot slotB = new TimeSlot("RES-B-D0", 5, day, "DAY", "RES-B", 480);

        assertTrue(MasterPlanCalendarMoments.sameCalendarStart(slotA, slotB));
        assertEqualsDay(slotA, slotB);
    }

    @Test
    void bomPlanningDayAllowsSameDayAcrossResources() {
        LocalDate day = LocalDate.of(2026, 6, 10);
        TimeSlot childSlot = new TimeSlot("RES-A-D0", 0, day, "DAY", "RES-A", 480);
        TimeSlot parentSlot = new TimeSlot("RES-B-D0", 5, day, "DAY", "RES-B", 480);

        assertFalse(MasterPlanCalendarMoments.violatesPlanningDayOrder(
                childSlot.getDate(),
                parentSlot.getDate()));
    }

    @Test
    void bomPlanningDayRejectsChildAfterParent() {
        LocalDate childDay = LocalDate.of(2026, 6, 11);
        LocalDate parentDay = LocalDate.of(2026, 6, 10);

        assertTrue(MasterPlanCalendarMoments.violatesPlanningDayOrder(childDay, parentDay));
    }

    @Test
    void bomPlanningDayAllowsChildBeforeParentDespiteIndexOrder() {
        LocalDate childDay = LocalDate.of(2026, 6, 9);
        LocalDate parentDay = LocalDate.of(2026, 6, 10);
        assertFalse(MasterPlanCalendarMoments.violatesPlanningDayOrder(childDay, parentDay));
    }

    private static void assertEqualsDay(TimeSlot a, TimeSlot b) {
        assertTrue(MasterPlanCalendarMoments.slotDayOrdinal(a) == MasterPlanCalendarMoments.slotDayOrdinal(b));
    }
}
