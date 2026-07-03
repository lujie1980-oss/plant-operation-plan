package com.plantops.scenario.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceCapacityAssignmentBuilderDaySegmentsTest {

    @Test
    void multiResourceUsesParallelCapacityForDaySegments() {
        assertEquals(1, ResourceCapacityAssignmentBuilder.daySegmentsForOperation(600, 480 + 480, true));
    }

    @Test
    void singleResourceUsesMachineCapacityForDaySegments() {
        assertEquals(2, ResourceCapacityAssignmentBuilder.daySegmentsForOperation(600, 480, true));
    }

    @Test
    void multiResourceHeavyLoadMayNeedSecondDaySegment() {
        assertEquals(2, ResourceCapacityAssignmentBuilder.daySegmentsForOperation(1000, 480 + 480, true));
    }

    @Test
    void unconstrainedAlwaysOneSegment() {
        assertEquals(1, ResourceCapacityAssignmentBuilder.daySegmentsForOperation(600, 480, false));
    }
}
