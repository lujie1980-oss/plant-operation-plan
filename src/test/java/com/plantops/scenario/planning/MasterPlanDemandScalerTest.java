package com.plantops.scenario.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasterPlanDemandScalerTest {

    @Test
    void scalesDownBy100() {
        assertEquals(6, MasterPlanDemandScaler.scaleMinutes(600, 0.01));
        assertEquals(362, MasterPlanDemandScaler.scaleMinutes(36194, 0.01));
    }

    @Test
    void keepsMinimumOneMinute() {
        assertEquals(1, MasterPlanDemandScaler.scaleMinutes(50, 0.01));
    }

    @Test
    void unityScaleIsNoOp() {
        assertEquals(600, MasterPlanDemandScaler.scaleMinutes(600, 1.0));
    }
}
