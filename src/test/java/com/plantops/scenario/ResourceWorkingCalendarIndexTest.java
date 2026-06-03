package com.plantops.scenario;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceWorkingCalendarIndexTest {

    @Test
    void snapForwardAlignsToNextOpenWindow() {
        LocalDate anchor = LocalDate.of(2026, 6, 1);
        ResourceWorkingCalendarIndex index = new ResourceWorkingCalendarIndex(
                anchor,
                Map.of(
                        "YD-13",
                        List.of(new ResourceWorkingCalendarIndex.MinuteWindow(480, 1200))));

        assertEquals(480, index.snapForward("YD-13", 100));
        assertEquals(600, index.snapForward("YD-13", 600));
        assertEquals(1200, index.snapForward("YD-13", 1200));
    }

    @Test
    void snapForwardWithoutCalendarLeavesMinuteUnchanged() {
        ResourceWorkingCalendarIndex index = ResourceWorkingCalendarIndex.empty();
        assertEquals(42, index.snapForward("UNKNOWN", 42));
    }
}
